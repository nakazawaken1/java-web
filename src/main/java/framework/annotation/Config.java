package framework.annotation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import framework.Log;
import framework.Reflector;
import framework.Tool;
import framework.Tuple;

/**
 * config file mapping
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {

    /**
     * @return file name(use class name if empty)
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
         * @return real properties
         */
        public static Properties inject(Class<?> clazz) {

            Properties sourceProperties = new Properties();

            /* load config files form Config or classname.config */
            String[] fs = Optional.ofNullable(clazz.getAnnotation(Config.class)).map(Config::value)
                    .orElse(new String[] { clazz.getSimpleName().toLowerCase() + ".config" });
            for (String f : fs) {
                sourceProperties.putAll(getProperties(f));
            }

            /* add system properties */
            System.getProperties().forEach((k, v) -> {
                String key = (String) k;
                if (key.startsWith("sys.")) {
                    sourceProperties.setProperty(key, (String) v);
                }
            });

            /* select db setting from sys.db.suffix */
            Optional<String> suffix = Tool.string(sourceProperties.getProperty("sys.db.suffix"));
            suffix.ifPresent(s -> {
                Reflector.fields(Sys.Db.class).forEach((name, field) -> {
                    if ("suffix".equals(name)) {
                        return;
                    }
                    Tool.string(sourceProperties.getProperty(name + s)).ifPresent(v -> sourceProperties.setProperty(name, v));
                });
            });

            /* resolve variables */
            for (;;) {
                boolean[] loop = { false };
                Set<String> missings = new LinkedHashSet<>();
                sourceProperties.entrySet().forEach(pair -> {
                    resolve((String) pair.getValue(), sourceProperties, value -> {
                        sourceProperties.setProperty((String) pair.getKey(), value);
                        loop[0] = true;
                    }, missings::add);
                });
                if (!loop[0]) {
                    missings.stream().map(key -> BEGIN + key + END + " cannot resolve.").forEach(Log::warning);
                    break;
                }
            }

            return inject(clazz, sourceProperties);
        }

        /**
         * inject from properties
         * 
         * @param clazz target class
         * @param properties source properties
         * @return real properties
         */
        public static Properties inject(Class<?> clazz, Properties properties) {
            defaultMap.put(clazz, String.join(Letters.CRLF, dump(clazz, true)));
            return Tool.peek(inject(clazz, properties, ""), p -> sourceMap.put(clazz, p));
        }

        /**
         * @param clazz target class
         * @param sort sort if true
         * @return lines
         */
        public static List<String> dump(Class<?> clazz, boolean sort) {
            return dump(clazz, "", sort);
        }

        /**
         * @param properties Properties
         * @param sort sort if true
         * @return lines
         */
        public static List<String> dump(Properties properties, boolean sort) {
            List<String> lines = new ArrayList<>();
            properties.forEach((key, value) -> lines.add(key + " = " + value));
            if (sort) {
                Collections.sort(lines);
            }
            return lines;
        }

        /**
         * @param <T> Return type
         * @param name property name
         * @return property value
         */
        @SuppressWarnings("unchecked")
        public static <T> Optional<T> get(String name) {
            Field field = fieldCache.computeIfAbsent(name, fullName -> {
                int classIndex = fullName.indexOf('.');
                int fieldIndex = fullName.lastIndexOf('.');
                if (classIndex < 0 || fieldIndex < 0) {
                    return null;
                }
                Class<?> clazz = classCache.computeIfAbsent(fullName.substring(0, fieldIndex), className -> {
                    Class<?> c = classCache.get(fullName.substring(0, classIndex));
                    if (classIndex < fieldIndex) {
                        for (String i : fullName.substring(classIndex + 1, fieldIndex).split("[.]")) {
                            if (c == null) {
                                return null;
                            }
                            c = Stream.of(c.getClasses()).filter(j -> i.equalsIgnoreCase(j.getSimpleName())).findAny().orElse(null);
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
            if (field == null) {
                return Optional.empty();
            }
            try {
                return Optional.ofNullable((T) field.get(null));
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
         * @return Properties
         */
        public static Properties getSource(Class<?> clazz) {
            return sourceMap.get(clazz);
        }

        /**
         * modifiers of Filed
         */
        static final Field modifiersField;
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
         * source properties
         */
        static final Map<Class<?>, Properties> sourceMap = new ConcurrentHashMap<>();

        /**
         * class cache
         */
        static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

        /**
         * field cache
         */
        static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

        static {
            try {
                modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
            } catch (NoSuchFieldException | SecurityException e) {
                throw new InternalError(e);
            }
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
            String newPrefix = prefix + clazz.getSimpleName().toLowerCase() + '.';
            classCache.put(newPrefix.substring(0, newPrefix.length() - 1), clazz);
            Stream.of(clazz.getDeclaredFields()).filter(f -> Modifier.isStatic(f.getModifiers())).forEach(f -> {
                String key = newPrefix + f.getName();
                String raw = properties.getProperty(key);
                int modifiers = f.getModifiers();
                if ((modifiers & Modifier.FINAL) == Modifier.FINAL) {
                    try {
                        modifiersField.setInt(f, modifiers & ~Modifier.FINAL); // must to be before Field.get
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new InternalError(e);
                    }
                }
                f.setAccessible(true);
                Object value;
                try {
                    value = f.get(null);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new InternalError(e);
                }
                if (properties.containsKey(key) || value == null) {
                    Class<?> type = f.getType();
                    if (type == Optional.class) {
                        value = raw == null ? Optional.empty() : Optional.of(getValue(f, Reflector.getGenericParameter(f, 0), raw));
                    } else if (type.isArray()) {
                        Class<?> componentType = type.getComponentType();
                        Object[] array = split(raw, f.getAnnotation(Separator.class)).map(i -> getValue(f, componentType, i)).toArray();
                        value = Array.newInstance(componentType, array.length);
                        int i = 0;
                        for (Object v : array) {
                            Array.set(value, i, v);
                            i++;
                        }
                    } else if (type == List.class) {
                        value = split(raw, f.getAnnotation(Separator.class)).map(i -> getValue(f, Reflector.getGenericParameter(f, 0), i))
                                .collect(Collectors.toList());
                    } else if (type == Set.class) {
                        value = split(raw, f.getAnnotation(Separator.class)).map(i -> getValue(f, Reflector.getGenericParameter(f, 0), i))
                                .collect(Collectors.toSet());
                    } else if (type == Map.class) {
                        value = split(raw, f.getAnnotation(Separator.class)).map(i -> {
                            String[] pair = i.split(Tool.val(f.getAnnotation(Separator.class),
                                    s -> s == null ? prefixDefault + pairDefault + suffixDefault : s.prefix() + s.pair() + s.suffix()));
                            return Tuple.of(getValue(f, Reflector.getGenericParameter(f, 0), pair[0]),
                                    getValue(f, Reflector.getGenericParameter(f, 1), pair[1]));
                        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    } else {
                        value = getValue(f, type, raw);
                    }
                    try {
                        f.set(null, value);
                    } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                        throw new InternalError(e);
                    }
                }
                realProperties.setProperty(key, toString(f, value));
            });
            Stream.of(clazz.getClasses()).filter(i -> !Enum.class.isAssignableFrom(i)).forEach(c -> realProperties.putAll(inject(c, properties, newPrefix)));
            return realProperties;
        }

        /**
         * inner use
         * 
         * @param clazz Target class
         * @param prefix Prefix of property-name
         * @param sort sort if true
         * @return lines
         */
        static List<String> dump(Class<?> clazz, String prefix, boolean sort) {
            String newPrefix = prefix + clazz.getSimpleName().replace('$', '.').toLowerCase() + '.';
            List<String> lines = new ArrayList<>();
            Stream.of(clazz.getDeclaredFields()).filter(f -> Modifier.isStatic(f.getModifiers())).forEach(f -> {
                try {
                    String key = newPrefix + f.getName();
                    Object value = f.get(null);
                    List<String> comments = Optional.ofNullable(f.getAnnotation(Help.class)).map(Help::value).map(Arrays::asList).orElse(null);
                    if (comments != null) {
                        Collections.reverse(comments);
                    }
                    lines.add('\b' + key + " = " + toString(f, value) + (comments == null ? "" : "\b# " + String.join("\b# ", comments)));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new InternalError(e);
                }
            });
            Stream.of(clazz.getClasses()).filter(c -> !Enum.class.isAssignableFrom(c)).forEach(c -> lines.addAll(dump(c, newPrefix, false)));
            if (sort) {
                Collections.sort(lines);
                return lines.stream().flatMap(line -> {
                    List<String> reverse = Arrays.asList(line.split("\b"));
                    Collections.reverse(reverse);
                    return reverse.stream();
                }).collect(Collectors.toList());
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
            return Optional.ofNullable(field.getAnnotation(Format.class)).map(Format::value).map(DateTimeFormatter::ofPattern);
        }

        /**
         * inner use
         * 
         * @param path Properties file path
         * @return Properties
         */
        static Properties getProperties(String path) {
            Properties p = new Properties();
            try (Reader reader = new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
                p.load(reader);
            } catch (IOException | NullPointerException e) {
                Log.warning("cannot read " + path);
            }
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
                return raw == null ? false : Boolean.parseBoolean(raw);
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
            return Stream.of(String.valueOf(text).split(pattern));
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
            char separator = Optional.ofNullable(field.getAnnotation(Separator.class)).map(Separator::value).orElse(valueDefault);
            if (clazz == Optional.class) {
                return ((Optional<?>) value).map(String::valueOf).orElse("");
            }
            if (clazz.isArray()) {
                StringBuilder s = new StringBuilder();
                for (int i = 0, i2 = Array.getLength(value); i < i2; i++) {
                    s.append(separator).append(Array.get(value, i));
                }
                return s.length() > 0 ? s.substring(1) : "";
            }
            if (clazz == List.class) {
                return ((List<?>) value).stream().map(String::valueOf).collect(Collectors.joining(String.valueOf(separator)));
            }
            if (clazz == Set.class) {
                return ((Set<?>) value).stream().map(String::valueOf).collect(Collectors.joining(String.valueOf(separator)));
            }
            if (clazz == Map.class) {
                char pairSeparator = Optional.ofNullable(field.getAnnotation(Separator.class)).map(Separator::pair).orElse(pairDefault);
                return ((Map<?, ?>) value).entrySet().stream().map(pair -> String.valueOf(pair.getKey()) + pairSeparator + pair.getValue())
                        .collect(Collectors.joining(String.valueOf(separator)));
            }
            if (value instanceof Temporal) {
                Optional<DateTimeFormatter> formatter = getFormat(field);
                if (formatter.isPresent()) {
                    return formatter.get().format((Temporal) value);
                }
            }
            return String.valueOf(value);
        }

        /**
         * variable bracket beginning mark
         */
        public static final String BEGIN = "${";

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
    }
}