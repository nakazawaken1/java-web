package framework;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.activation.MimetypesFileTypeMap;

import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig.Feature;

import framework.Try.TryTriConsumer;

/**
 * utility
 */
public class Tool {

    /**
     * not empty string
     */
    public static final Predicate<String> notEmpty = ((Predicate<String>) String::isEmpty).negate();

    /**
     * PrintStream to String
     * 
     * @param action write to PrintStream
     * @return wrote text
     */
    public static String print(Consumer<PrintStream> action) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(out, false, StandardCharsets.UTF_8.name())) {
            action.accept(ps);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * get first non-null value
     * 
     * @param value value
     * @param suppliers value suppliers
     * @return value
     */
    @SafeVarargs
    public static <T> Optional<T> or(T value, Supplier<T>... suppliers) {
        for (Supplier<T> i : suppliers) {
            if (value != null) {
                break;
            }
            value = i.get();
        }
        return Optional.ofNullable(value);
    }

    /**
     * get non-empty and non-null string
     * 
     * @param value value
     * @return String or empty if null or empty
     */
    public static Optional<String> string(Object value) {
        return value == null ? Optional.empty() : Optional.ofNullable(value.toString()).filter(notEmpty);
    }

    /**
     * get integer
     * 
     * @param value value
     * @return integer
     */
    public static Optional<Integer> integer(Object value) {
        try {
            return value == null ? Optional.empty() : Optional.of(Integer.parseInt(value.toString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * @param values values
     * @return array
     */
    @SafeVarargs
    public static <T> T[] array(T... values) {
        return values;
    }

    /**
     * @param o object
     * @param hashes avoid loop
     * @return text
     */
    @SafeVarargs
    public static String dump(Object o, Set<Object>... hashes) {
        final Set<Object> cache = hashes.length > 0 ? hashes[0] : new HashSet<>();
        return Stream.of(o.getClass().getDeclaredFields()).map(field -> {
            String value = "null";
            try {
                Object object = field.get(o);
                if (object != null) {
                    if (object.getClass().getMethod("toString").getDeclaringClass() != Object.class) {
                        value = object.toString();
                    } else if (cache.contains(object)) {
                        value = "(loop)";
                    } else {
                        cache.add(object);
                        value = dump(object, cache);
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException | SecurityException e) {
            }
            return field.getName() + ": " + value;
        }).collect(Collectors.joining("\n"));
    }

    /**
     * Enumeration to Stream
     *
     * @param <T> value type
     *
     * @param e Enumeration
     * @return Stream
     */
    public static <T> Stream<T> stream(Enumeration<T> e) {
        return StreamSupport.stream(new Spliterator<T>() {

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (e.hasMoreElements()) {
                    action.accept(e.nextElement());
                    return true;
                }
                return false;
            }

            @Override
            public Spliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return Spliterator.ORDERED | Spliterator.SIZED;
            }
        }, false);
    }

    /**
     * @param writer writer
     * @param text text
     * @param replacer replacer(writer, expression, prefix)
     * @param closures closures
     */
    public static void printFormat(PrintWriter writer, String text, TryTriConsumer<PrintWriter, String, String> replacer, String... closures) {
        for (boolean loop = true; loop;) {
            loop = false;
            for (int i = 0; i < closures.length; i += 2) {
                String prefix = closures[i];
                String suffix = closures[i + 1];
                int begin = text.indexOf(prefix);
                if (begin < 0) {
                    continue;
                }
                int end = text.indexOf(suffix, begin);
                if (end < 0) {
                    continue;
                }
                writer.print(text.substring(0, begin));
                if (replacer != null) {
                    String tag = text.substring(begin + prefix.length(), end).trim();
                    Try.c(replacer).accept(writer, tag, prefix);
                }
                text = text.substring(end + suffix.length());
                loop = true;
            }
        }
        if (!text.isEmpty()) {
            writer.print(text);
        }
    }

    /**
     * @param left left trimming characters
     * @param text target text
     * @param right right trimming characters
     * @return trimed text
     */
    public static String trim(String left, String text, String right) {
        if (text == null) {
            return null;
        }
        int start = 0;
        int end = text.length() - 1;
        if (left != null) {
            while (start <= end) {
                if (left.indexOf(text.charAt(start)) < 0) {
                    break;
                }
                start++;
            }
        }
        if (right != null) {
            while (start <= end) {
                if (right.indexOf(text.charAt(end)) < 0) {
                    break;
                }
                end--;
            }
        }
        return text.substring(start, end + 1);
    }

    /**
     * MIME type from file extension(using META-INF/mime.types)
     *
     * @param file file name with extension
     * @return MIME type
     */
    public static String getContentType(String file) {
        return MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(file);
    }

    /**
     * @param text text
     * @param suffix suffix
     * @return text
     */
    public static String suffix(String text, String suffix) {
        return text.endsWith(suffix) ? text : text + suffix;
    }

    /**
     * @param a first value
     * @param b second value
     * @return pair tuple
     */
    public static <K, V> Pair<K, V> pair(K a, V b) {
        return new Pair<>(a, b);
    }

    /**
     * @param a first value
     * @param b second value
     * @param c third value
     * @return trio tuple
     */
    public static <T, U, V> Trio<T, U, V> trio(T a, U b, V c) {
        return new Trio<>(a, b, c);
    }

    /**
     * @param array array
     * @param i index(support negative value)
     * @return element
     */
    public static <T> T at(T[] array, int i) {
        return array[i < 0 ? array.length + i : i];
    }

    /**
     * list classes from package
     *
     * @param packageName package name
     * @return class stream(must to close)
     */
    public static Stream<Class<?>> getClasses(String packageName) {
        String prefix = Tool.string(packageName).map(i -> i + '.').orElse("");
        return Config.toURL(packageName.replace('.', '/')).map(Try.f(i -> {
            File file = new File(i.toURI());
            return (file.isDirectory() ? getResourcesFromFolder(file) : getResourcesFromJar(file))
                    .map(f -> prefix + f.substring(0, f.length() - ".class".length())).<Class<?>>map(Try.f(Class::forName));
        })).orElse(Stream.empty());
    }

    /**
     * list files
     *
     * @param location location
     * @return file name stream(must to close)
     */
    public static Stream<String> getResources(String location) {
        return Config.toURL(location).map(Try.f(i -> {
            File file = new File(i.toURI());
            return file.isDirectory() ? getResourcesFromFolder(file) : getResourcesFromJar(file);
        })).orElse(Stream.empty());
    }

    /**
     * list files in jar
     *
     * @param jar jar file
     * @return file name stream(must to close)
     */
    private static Stream<String> getResourcesFromJar(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            return zip.stream().map(ZipEntry::getName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * list files in folder
     *
     * @param folder folder
     * @return file name stream(must to close)
     */
    private static Stream<String> getResourcesFromFolder(File folder) {
        try {
            return Files.list(folder.toPath()).map(path -> path.getFileName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * build map<String, Object>
     *
     * @param keyValues key, value, key, value...
     * @return map
     */
    public static Map<String, Object> jsonMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    /**
     * @param o object
     * @return json
     */
    public static String json(Object o) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(JsonMethod.FIELD, Visibility.PUBLIC_ONLY);
        mapper.configure(Feature.FAIL_ON_EMPTY_BEANS, false);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (IOException e) {
            return Objects.toString(o);
        }
    }

    /**
     * @param left first stream
     * @param right second stream
     * @return zipped stream
     */
    public static <T, U> Stream<Pair<T, U>> zip(Stream<T> left, Stream<U> right) {
        Iterator<T> l = left.iterator();
        Iterator<U> r = right.iterator();
        Iterator<Pair<T, U>> iterator = new Iterator<Pair<T, U>>() {
            @Override
            public boolean hasNext() {
                return l.hasNext() && r.hasNext();
            }

            @Override
            public Pair<T, U> next() {
                return pair(l.next(), r.next());
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL | Spliterator.ORDERED), false);
    }

    /**
     * @param left first stream
     * @param right second stream
     * @return zipped stream
     */
    public static <T, U> Stream<Pair<T, U>> zipLong(Stream<T> left, Stream<U> right) {
        Iterator<T> l = left.iterator();
        Iterator<U> r = right.iterator();
        Iterator<Pair<T, U>> iterator = new Iterator<Pair<T, U>>() {
            @Override
            public boolean hasNext() {
                return l.hasNext() || r.hasNext();
            }

            @Override
            public Pair<T, U> next() {
                return pair(l.hasNext() ? l.next() : null, r.hasNext() ? r.next() : null);
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL | Spliterator.ORDERED), false);
    }

    /**
     * get next time
     * 
     * @param text text
     * @param from start point
     * @return milliseconds
     */
    public static long nextMillis(String text, ZonedDateTime from) {
        String value = text.trim().toUpperCase();
        if ("DHMS".indexOf(value.charAt(value.length() - 1)) >= 0) {
            Duration interval = Duration.parse(value.endsWith("D") ? "P" + value : "PT" + value);
            Logger.getGlobal().info("interval: " + interval + ", next: " + from.plus(interval));
            return interval.toMillis();
        } else {
            ZonedDateTime next = from;
            int timeIndex = value.indexOf(':');
            if (timeIndex >= 0) { /* has time */
                while (timeIndex > 0) {
                    timeIndex--;
                    if ("0123456789".indexOf(value.charAt(timeIndex)) < 0) {
                        break;
                    }
                }
                List<ChronoField> fields = Arrays.asList(ChronoField.HOUR_OF_DAY, ChronoField.MINUTE_OF_HOUR, ChronoField.SECOND_OF_MINUTE);
                ChronoUnit[] units = { ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS };
                ChronoField[] last = { null };
                Stream<Long> values = Stream.of(value.substring(timeIndex).split("[^0-9]+")).filter(Tool.notEmpty).map(Long::valueOf);
                ZonedDateTime calc = Tool.zip(fields.stream(), values).peek(i -> last[0] = i.getKey())
                        .reduce(next, (i, pair) -> i.with(pair.getKey(), pair.getValue()), (i, j) -> i).truncatedTo(units[fields.indexOf(last[0]) + 1]);
                if (from.isAfter(calc)) {
                    next = calc.plus(1, ChronoUnit.DAYS);
                } else {
                    next = calc;
                }
                value = value.substring(0, timeIndex);
            }
            if (!value.isEmpty()) {
                List<ChronoField> fields = Arrays.asList(ChronoField.DAY_OF_MONTH, ChronoField.MONTH_OF_YEAR, ChronoField.YEAR);
                ChronoField[] last = { null };
                ChronoUnit[] units = { ChronoUnit.MONTHS, ChronoUnit.YEARS, null };
                Stream<Long> values = Stream.of(value.split("[^0-9]+")).filter(Tool.notEmpty).map(Long::valueOf);
                ZonedDateTime calc = Tool.zip(fields.stream(), values).peek(i -> last[0] = i.getKey()).reduce(next,
                        (i, pair) -> i.with(pair.getKey(), pair.getValue()), (i, j) -> i);
                if (last[0] != null && next.isAfter(calc)) {
                    next = calc.plus(1, units[fields.indexOf(last[0])]);
                } else {
                    next = calc;
                }
            }
            Logger.getGlobal().info("next: " + next);
            return ChronoUnit.MILLIS.between(from, next);
        }
    }

    /**
     * test
     * 
     * @param args text
     */
    public static void main(String[] args) {
        Stream.of(null, "", "Abc", "abcDef", "AbcDefG", "URLEncoder").map(Tool::camelToSnake).forEach(Logger.getGlobal()::info);
        Stream.of(null, "", "abc", "abc___def_", "_abc_def_").map(Tool::snakeToCamel).forEach(Logger.getGlobal()::info);
        // Stream.concat(Stream.of("1d", "2h", "3m", "4s", "1", "1/1", "12:00", "01:02:03"), Stream.of(args)).forEach(text -> Tool.nextMillis(text,
        // ZonedDateTime.now()));
    }

    /**
     * @param text camel case text
     * @return snake case text
     */
    public static String camelToSnake(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        StringBuilder result = new StringBuilder(length + length);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && (i + 1 >= length || !Character.isUpperCase(text.charAt(i + 1)))) {
                    result.append("_");
                }
                result.append(Character.toLowerCase(c));
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    /**
     * @param text snake case text
     * @return camel case text
     */
    public static String snakeToCamel(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        StringBuilder result = new StringBuilder(length + length);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '_') {
                if (i + 1 < length) {
                    i++;
                    c = text.charAt(i);
                    if (c != '_') {
                        result.append(Character.toUpperCase(c));
                    }
                }
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }
}
