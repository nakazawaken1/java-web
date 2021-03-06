package framework.annotation;

import java.io.IOException;
import java.io.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import app.config.Sys;
import framework.Db;
import framework.Formatter;
import framework.Log;
import framework.Message;
import framework.Reflector;
import framework.Session;
import framework.Tool;
import framework.Try;
import framework.Tuple;

/**
 * config file mapping
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {

    /**
     * @return file names(use class name if empty)
     */
    String[] value() default {};

    /**
     * properties inject to static fields
     */
    class Injector {
        /**
         * inject from class
         * 
         * @param clazz target class(get source properties by annotation)
         */
        public static void inject(Class<?> clazz) {

            Properties sourceProperties = inject(clazz, new Properties(), "");

            /* load config files form Config or classname.config */
            String[] fs = Tool.of(clazz.getAnnotation(Config.class))
                .map(Config::value)
                .filter(a -> a.length > 0)
                .orElse(Tool.array(Tool.fullName(clazz)
                    .toLowerCase(Locale.ENGLISH) + ".config"));
            for (String f : fs) {
                sourceProperties.putAll(getProperties(f));
            }

            /* resolve variables */
            for (;;) {
                boolean[] loop = { false };
                Set<String> missings = new LinkedHashSet<>();
                sourceProperties.entrySet()
                    .forEach(pair -> {
                        resolve((String) pair.getValue(), sourceProperties, value -> {
                            sourceProperties.setProperty((String) pair.getKey(), value);
                            loop[0] = true;
                        }, missings::add);
                    });
                if (!loop[0]) {
                    missings.stream()
                        .map(key -> BEGIN + key + END + " cannot resolve.")
                        .forEach(Log::warning);
                    break;
                }
            }

            defaultMap.put(clazz, String.join(Letters.CRLF, dumpConfig(clazz, true)));
            Map<String, Properties> propertiesMap = Tool.map("", sourceProperties);
            Stream.of(fs)
                .map(s -> Tuple.of(Tool.getFolder(s), Tool.getName(s), Tool.getExtension(s)))
                .collect(Collectors.groupingBy(t -> t.l))
                .entrySet()
                .forEach(entry -> {
                    String folder = entry.getKey();
                    List<Tuple<String, String>> nameExtension = entry.getValue()
                        .stream()
                        .map(t -> Tuple.of(t.r.l, t.r.r))
                        .collect(Collectors.toList());
                    try (Stream<String> list = Tool.getResources(folder)) {
                        list.map(i -> Tuple.of(i, nameExtension.stream()
                            .filter(ne -> i.startsWith(ne.l) && i.endsWith(ne.r))
                            .findFirst()
                            .orElse(null)))
                            .filter(t -> t.r != null)
                            .map(t -> Tuple.of(t.l, folder.length() + t.r.l.length() + 1, t.l.length() - t.r.r.length()))
                            .filter(t -> t.r.l < t.r.r)
                            .forEach(t -> propertiesMap.compute(t.l
                                .substring(t.r.l, t.r.r), (k, v) -> v == null ? getProperties(t.l) : Tool.peek(v, vv -> vv.putAll(getProperties(t.l)))));
                    }
                });
            sourceMap.put(clazz, propertiesMap);

            inject(clazz, getSource(clazz, Session.currentLocale()), "");
        }

        /**
         * Set configuration value
         * 
         * @param name Name
         * @param value Value
         * @param locale Locale
         */
        public static void set(String name, String value, String locale) {
            Tool.of(getField(name))
                .ifPresent(field -> {
                    if (locale.isEmpty() && !Message.class.isAssignableFrom(field.getDeclaringClass())) {
                        set(field, name, value);
                    }
                });
            Tool.of(classCache.get(Tool.splitAt(name, "[.]", 0)))
                .map(clazz -> sourceMap.get(clazz)
                    .computeIfAbsent(locale, k -> new Properties()))
                .ifPresent(map -> map.put(name, value));
        }

        /**
         * Load database config
         */
        public static void loadDb() {
            try (Db db = Db.connect()) {
                String now = Tool.now(14);
                db.from("t_config")
                    .where("start_at", "<=", now)
                    .where("end_at", ">", now)
                    .rows(rs -> {
                        String name = Tool.string(rs.getString("name"))
                            .orElse("");
                        String value = Tool.string(rs.getString("value"))
                            .map(s -> s.replace("\\n", "\n")
                                .replace("\\r", "\r"))
                            .orElse("");
                        String locale = Tool.string(rs.getString("locale"))
                            .orElse("");
                        set(name, value, locale);
                    });
                sourceCache.clear();
            }
        }

        /**
         * Load system properties
         */
        public static void loadSystemProperties() {
            System.getProperties()
                .forEach((name, value) -> {
                    if (configKeys.contains(name) || ((String) name).startsWith("Sys.Db.")) { // overwrite only
                        set((String) name, (String) value, "");
                    }
                });
            sourceCache.clear();
        }

        /**
         * dump config
         * 
         * @param clazz target class
         * @param sort sort if true
         * @return lines
         */
        public static List<String> dumpConfig(Class<?> clazz, boolean sort) {
            return dumpConfig(clazz, "", sort);
        }

        /**
         * @return message dump
         */
        public static String[] dumpMessage() {
            Set<Locale> locales = sourceMap.entrySet()
                .stream()
                .flatMap(entry -> entry.getValue()
                    .keySet()
                    .stream())
                .map(Locale::forLanguageTag)
                .collect(Collectors.toSet());
            Set<Class<?>> classes = sourceMap.keySet();
            return locales.stream()
                .flatMap(locale -> Stream.concat(Stream.of("[" + Tool.string(locale)
                    .orElse("default") + "]"), classes.stream()
                        .flatMap(clazz -> dumpMessage(getSource(clazz, locale), true).stream())))
                .toArray(String[]::new);
        }

        /**
         * @param name Field full name
         * @return Field
         */
        public static Field getField(String name) {
            return fieldCache.computeIfAbsent(name, fullName -> {
                int classIndex = fullName.indexOf('.');
                int fieldIndex = fullName.lastIndexOf('.');
                if (classIndex < 0 || fieldIndex < 0) {
                    return null;
                }
                Class<?> clazz = classCache.computeIfAbsent(fullName.substring(0, fieldIndex), className -> {
                    Class<?> c = classCache.get(fullName.substring(0, classIndex));
                    if (classIndex < fieldIndex) {
                        for (String i : fullName.substring(classIndex + 1, fieldIndex)
                            .split("[.]")) {
                            if (c == null) {
                                return null;
                            }
                            c = Stream.of(c.getClasses())
                                .filter(j -> i.equals(j.getSimpleName()))
                                .findAny()
                                .orElse(null);
                        }
                    }
                    return c;
                });
                if (clazz == null) {
                    return null;
                }
                try {
                    Field f = clazz.getDeclaredField(fullName.substring(fieldIndex + 1));
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException | SecurityException e) {
                    return null;
                }
            });
        }

        /**
         * @param <T> Return type
         * @param name property name
         * @param locale locale
         * @return property value
         */
        @SuppressWarnings("unchecked")
        public static <T> Optional<T> getValue(String name, Locale locale) {
            Field field = getField(name);
            if (field == null) {
                return Optional.empty();
            }
            try {
                return Tool.of(field.get(null))
                    .map(i -> (T) (i instanceof Message ? ((Message) i).message(locale) : i));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                return Optional.empty();
            }
        }

        /**
         * @param clazz Class
         * @return Default settings
         */
        public static String getDefault(Class<?> clazz) {
            return defaultMap.get(clazz);
        }

        /**
         * @param clazz Class
         * @param locale locale
         * @return Properties
         */
        public static Properties getSource(Class<?> clazz, Locale locale) {
            Map<String, Properties> map = Objects.requireNonNull(sourceMap.get(clazz));
            return sourceCache.computeIfAbsent(Tuple.of(clazz, locale), t -> {
                Properties p = new Properties();
                map.entrySet()
                    .stream()
                    .filter(pair -> locale.toString()
                        .startsWith(pair.getKey()))
                    .sorted((a, b) -> a.getKey()
                        .compareTo(b.getKey()))
                    .map(Map.Entry::getValue)
                    .forEach(p::putAll);
                return p;
            });
        }

        /**
         * default value of Separator.prefix
         */
        static final String prefixDefault;
        /**
         * default value of Separator.value
         */
        static final char valueDefault;
        /**
         * default value of Separator.suffix
         */
        static final String suffixDefault;
        /**
         * default value of Separator.value
         */
        static final char pairDefault;

        /**
         * default settings
         */
        static final Map<Class<?>, String> defaultMap = new ConcurrentHashMap<>();

        /**
         * source properties(class: (locale prefix: properties))
         */
        static final Map<Class<?>, Map<String, Properties>> sourceMap = new ConcurrentHashMap<>();

        /**
         * source cache
         */
        static final Map<Tuple<Class<?>, Locale>, Properties> sourceCache = new ConcurrentHashMap<>();

        /**
         * class cache
         */
        static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

        /**
         * field cache
         */
        static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

        /**
         * Config keys(except message keys)
         */
        static final Set<String> configKeys = new HashSet<>();

        static {
            prefixDefault = Reflector.getDefaultValue(Separator.class, "prefix");
            valueDefault = Reflector.getDefaultValue(Separator.class, "value");
            suffixDefault = Reflector.getDefaultValue(Separator.class, "suffix");
            pairDefault = Reflector.getDefaultValue(Separator.class, "pair");
        }

        /**
         * inner use
         * 
         * @param clazz Target class
         * @param properties Source properties
         * @param prefix Prefix of property-name
         * @return real properties
         */
        static Properties inject(Class<?> clazz, Properties properties, String prefix) {
            Properties realProperties = new Properties();
            realProperties.putAll(properties);
            String newPrefix = prefix + clazz.getSimpleName() + '.';
            classCache.put(newPrefix.substring(0, newPrefix.length() - 1), clazz);
            if (!Enum.class.isAssignableFrom(clazz) || Message.class.isAssignableFrom(clazz)) {
                if (Message.class.isAssignableFrom(clazz)) {
                    Stream.of(clazz.getEnumConstants())
                        .forEach(i -> realProperties.put(newPrefix + ((Enum<?>) i).name(), ((Message) i).defaultMessage()));
                } else {
                    Reflector.fields(clazz).values().stream()
                        .filter(f -> Modifier.isStatic(f.getModifiers()))
                        .forEach(f -> {
                            f.setAccessible(true);
                            String key = newPrefix + f.getName();
                            String raw = properties.getProperty(key);
                            Object value;
                            try {
                                value = f.get(null);
                            } catch (IllegalArgumentException | IllegalAccessException e) {
                                throw new InternalError(e);
                            }
                            if (!Modifier.isFinal(f.getModifiers()) && (properties.containsKey(key) || value == null)) {
                                set(f, key, raw);
                            } else {
                                configKeys.add(key);
                            }
                            realProperties.setProperty(key, toString(f, value));
                        });
                }
            }
            Stream.of(clazz.getClasses())
                .forEach(c -> realProperties.putAll(inject(c, properties, newPrefix)));
            return realProperties;
        }

        /**
         * @param field Field
         * @param key Key
         * @param text Value
         */
        static void set(Field field, String key, String text) {
            configKeys.add(key);
            Class<?> type = field.getType();
            Object value;
            if (type == Optional.class) {
                value = Tool.string(text)
                    .map(s -> getValue(field, Reflector.getGenericParameter(field, 0), s));
            } else if (type.isArray()) {
                Class<?> componentType = type.getComponentType();
                Object[] array = split(text, field.getAnnotation(Separator.class)).map(i -> getValue(field, componentType, i))
                    .toArray();
                value = Array.newInstance(componentType, array.length);
                int i = 0;
                for (Object v : array) {
                    Array.set(value, i, v);
                    i++;
                }
            } else if (type == List.class) {
                value = split(text, field.getAnnotation(Separator.class)).map(i -> getValue(field, Reflector.getGenericParameter(field, 0), i))
                    .collect(Collectors.toList());
            } else if (type == Set.class) {
                value = split(text, field.getAnnotation(Separator.class)).map(i -> getValue(field, Reflector.getGenericParameter(field, 0), i))
                    .collect(LinkedHashSet::new, (set, v) -> set.add(v), Set::addAll);
            } else if (type == Map.class) {
                value = split(text, field.getAnnotation(Separator.class)).map(i -> {
                    String[] pair = i.split(Tool.val(field
                        .getAnnotation(Separator.class), s -> s == null ? prefixDefault + pairDefault + suffixDefault : s.prefix() + s.pair() + s.suffix()));
                    return Tuple.of(getValue(field, Reflector.getGenericParameter(field, 0), pair[0]), getValue(field, Reflector
                        .getGenericParameter(field, 1), pair[1]));
                })
                    .collect(LinkedHashMap::new, (map, tuple) -> map.put(tuple.l, tuple.r), Map::putAll);
            } else {
                value = getValue(field, type, text);
            }
            try {
                field.set(null, value);
            } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                throw new InternalError(e);
            }
        }

        /**
         * inner use
         * 
         * @param clazz Target class
         * @param prefix Prefix of property-name
         * @param sort sort if true
         * @return lines
         */
        static List<String> dumpConfig(Class<?> clazz, String prefix, boolean sort) {
            String newPrefix = prefix + clazz.getSimpleName()
                .replace('$', '.') + '.';
            List<String> lines = new ArrayList<>();
            if (!Enum.class.isAssignableFrom(clazz)) {
                Reflector.fields(clazz).values().stream()
                    .filter(f -> Modifier.isStatic(f.getModifiers()))
                    .forEach(f -> {
                        try {
                            String key = newPrefix + f.getName();
                            Object value = f.get(null);
                            List<String> comments = Tool.of(f.getAnnotation(Help.class))
                                .map(Help::value)
                                .map(Arrays::asList)
                                .orElse(null);
                            if (comments != null) {
                                Collections.reverse(comments);
                            }
                            lines.add('\b' + key + " = " + toString(f, value) + (comments == null ? "" : "\b# " + String.join("\b# ", comments)));
                        } catch (IllegalArgumentException | IllegalAccessException e) {
                            throw new InternalError(e);
                        }
                    });
            }
            Stream.of(clazz.getClasses())
                .forEach(c -> lines.addAll(dumpConfig(c, newPrefix, false)));
            if (sort) {
                Collections.sort(lines);
                return lines.stream()
                    .flatMap(line -> {
                        List<String> reverse = Tool.list(line.split("\b"));
                        Collections.reverse(reverse);
                        return reverse.stream();
                    })
                    .collect(Collectors.toList());
            }
            return lines;
        }

        /**
         * inner use
         * 
         * @param properties Properties
         * @param sort sort if true
         * @return lines
         */
        static List<String> dumpMessage(Properties properties, boolean sort) {
            List<String> lines = new ArrayList<>();
            properties.forEach((key, value) -> {
                if (!configKeys.contains(key)) {
                    lines.add(key + " = " + value);
                }
            });
            if (sort) {
                Collections.sort(lines);
            }
            return lines;
        }

        /**
         * inner use
         * 
         * @param field Field
         * @return DateTimeFormatter or empty
         */
        static Optional<DateTimeFormatter> getFormat(Field field) {
            return Tool.of(field.getAnnotation(Format.class))
                .map(Format::value)
                .map(DateTimeFormatter::ofPattern);
        }

        /**
         * inner use
         * 
         * @param path Properties file path
         * @return Properties
         */
        static Properties getProperties(String path) {
            Properties p = new Properties();
            Tool.ifPresentOr(Tool.toURL(path), url -> {
                Log.info("config load: " + url);
                try (Reader reader = Tool.newReader(url.openStream())) {
                    p.load(reader);
                } catch (IOException e) {
                    Log.warning(e, () -> "load error");
                }
            }, () -> Log.info("config scan: " + path));
            return p;
        }

        /**
         * inner use
         * 
         * @param field Field
         * @param type Value type
         * @param raw String value
         * @return Value
         */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        static Object getValue(Field field, Class<?> type, String raw) {
            if (type == Integer.class || type == int.class) {
                return raw == null ? 0 : Integer.parseInt(raw);
            } else if (type == Byte.class || type == byte.class) {
                return raw == null ? (byte) 0 : Byte.parseByte(raw);
            } else if (type == Short.class || type == short.class) {
                return raw == null ? (short) 0 : Short.parseShort(raw);
            } else if (type == Long.class || type == long.class) {
                return raw == null ? 0L : Long.parseLong(raw);
            } else if (type == Float.class || type == float.class) {
                return raw == null ? 0f : Float.parseFloat(raw);
            } else if (type == Double.class || type == double.class) {
                return raw == null ? 0.0 : Double.parseDouble(raw);
            } else if (type == Character.class || type == char.class) {
                return raw != null && raw.length() > 0 ? raw.charAt(0) : '\0';
            } else if (type == Boolean.class || type == boolean.class) {
                return Boolean.parseBoolean(raw);
            } else if (type == String.class) {
                return raw == null ? "" : raw;
            } else if (type == LocalDate.class) {
                return raw == null ? LocalDate.now() : LocalDate.parse(raw, getFormat(field).orElse(DateTimeFormatter.ISO_LOCAL_DATE));
            } else if (type == LocalDateTime.class) {
                return raw == null ? LocalDateTime.now() : LocalDateTime.parse(raw, getFormat(field).orElse(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else if (type == LocalTime.class) {
                return raw == null ? LocalTime.now() : LocalTime.parse(raw, getFormat(field).orElse(DateTimeFormatter.ISO_LOCAL_TIME));
            } else if (type == ZonedDateTime.class) {
                return raw == null ? ZonedDateTime.now() : ZonedDateTime.parse(raw, getFormat(field).orElse(DateTimeFormatter.ISO_ZONED_DATE_TIME));
            } else if (type == OffsetDateTime.class) {
                return raw == null ? OffsetDateTime.now() : OffsetDateTime.parse(raw, getFormat(field).orElse(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            } else if (type == OffsetTime.class) {
                return raw == null ? OffsetTime.now() : OffsetTime.parse(raw, getFormat(field).orElse(DateTimeFormatter.ISO_OFFSET_TIME));
            } else if (Enum.class.isAssignableFrom(type)) {
                return raw == null ? type.getEnumConstants()[0] : Enum.valueOf((Class<Enum>) type, raw);
            } else if (type == DateTimeFormatter.class) {
                return raw == null ? DateTimeFormatter.BASIC_ISO_DATE : DateTimeFormatter.ofPattern(raw);
            } else if (type == Pattern.class) {
                return Pattern.compile(raw == null ? ".*" : raw);
            } else if (type == Level.class) {
                return raw == null ? Level.INFO : Level.parse(raw);
            } else if (type == Charset.class) {
                return raw == null ? Charset.defaultCharset() : Charset.forName(raw);
            } else if (type == URL.class) {
                return raw == null ? null
                        : Try.<String, URL>f(URL::new)
                            .apply(raw);
            } else {
                return raw;
            }
        }

        /**
         * inner use
         * 
         * @param text Text
         * @param separator Separator(Regular expression)
         * @return Splited text
         */
        static Stream<String> split(String text, Separator separator) {
            if (text == null) {
                return Stream.empty();
            }
            String pattern;
            if (separator == null) {
                pattern = prefixDefault + valueDefault + suffixDefault;
            } else {
                pattern = separator.prefix() + separator.value() + separator.suffix();
            }
            String[] items = String.valueOf(text).split(pattern);
            if(items.length == 1 && (items[0] == null || items[0].isEmpty())) {
            	return Stream.empty();
            }
            return Stream.of(items);
        }

        /**
         * inner use
         * 
         * @param field Field
         * @param value Value
         * @return string value
         */
        static String toString(Field field, Object value) {
            if (value == null || value == Optional.empty()) {
                return "";
            }
            Class<?> clazz = field.getType();
            if (clazz == Optional.class) {
                return ((Optional<?>) value).map(String::valueOf)
                    .orElse("");
            }
            char separator = Tool.of(field.getAnnotation(Separator.class))
                .map(Separator::value)
                .orElse(valueDefault);
            if (clazz.isArray()) {
                StringBuilder s = new StringBuilder();
                for (int i = 0, i2 = Array.getLength(value); i < i2; i++) {
                    s.append(separator)
                        .append(Array.get(value, i));
                }
                return s.length() > 0 ? s.substring(1) : "";
            }
            if (clazz == List.class) {
                return ((List<?>) value).stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(String.valueOf(separator)));
            }
            if (clazz == Set.class) {
                return ((Set<?>) value).stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(String.valueOf(separator)));
            }
            if (clazz == Map.class) {
                char pairSeparator = Tool.of(field.getAnnotation(Separator.class))
                    .map(Separator::pair)
                    .orElse(pairDefault);
                return Tool.peek(new StringBuilder(), s -> ((Map<?, ?>) value).forEach((k, v) -> s.append(separator)
                    .append(k)
                    .append(pairSeparator)
                    .append(v)))
                    .substring(1);
            }
            if (value instanceof Temporal) {
                Optional<DateTimeFormatter> formatter = getFormat(field);
                if (formatter.isPresent()) {
                    return formatter.get()
                        .format((Temporal) value);
                }
            }
            if (value instanceof DateTimeFormatter) {
                return Tool.val(((DateTimeFormatter) value).toString(), s -> Tool.formatCache.getOrDefault(s, s));
            }
            return String.valueOf(value);
        }

        /**
         * variable bracket beginning mark
         */
        public static final String BEGIN = "{";

        /**
         * variable bracket ending mark
         */
        public static final String END = "}";

        /**
         * resolve variable
         *
         * @param value value
         * @param source variables
         * @param changed changed action
         * @param missing missing action
         */
        static void resolve(String value, Properties source, Consumer<String> changed, Consumer<String> missing) {
            boolean isChanged = false;
            for (;;) {
                int start = value.indexOf(BEGIN);
                if (start < 0) {
                    break;
                }
                start += BEGIN.length();
                int end = value.indexOf(END, start);
                if (end < 0) {
                    break;
                }
                String key = value.substring(start, end);
                if (!source.containsKey(key)) {
                    if (missing != null) {
                        missing.accept(key);
                    }
                    break;
                }
                String replace = source.getProperty(key);
                int loop = replace.indexOf(BEGIN);
                if (loop >= 0 && loop < replace.indexOf(END)) {
                    if (missing != null) {
                        missing.accept(key);
                    }
                    break;
                }
                isChanged = true;
                value = value.substring(0, start - BEGIN.length()) + replace + value.substring(end + END.length());
            }
            if (isChanged) {
                changed.accept(value);
            }
        }
        
        /**
         * configuration class list
         */
        public static final List<Class<?>> classes = Tool.list();

		/**
		 * @param packages configuration class packages
		 */
		public static void setup(String... packages) {
	        try (Stream<Class<?>> cs = Tool.getClasses(Sys.class.getPackage()
	            .getName())
	            .filter(c -> Tool.fullName(c)
	                .indexOf('.') < 0)) {
	            classes.addAll(cs.peek(Config.Injector::inject)
	                .peek(c -> Formatter.elClassMap.put(c.getSimpleName(), c))
	                .collect(Collectors.toList()));
	        }

	        /* load system properties */
	        Config.Injector.loadSystemProperties();
		}
    }
}
