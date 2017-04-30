package framework;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.activation.MimetypesFileTypeMap;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Authenticator;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import app.config.Sys;
import framework.Try.TryTriConsumer;

/**
 * utility
 */
public class Tool {

    /**
     * not empty string
     */
    public static final Predicate<String> notEmpty = Tool.not(String::isEmpty);

    /**
     * @param predicate predicate
     * @return negate predicate
     */
    public static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }

    /**
     * @param relativePath relative path
     * @return url
     */
    public static Optional<URL> toURL(String... relativePath) {
        String path = Stream.of(relativePath).map(i -> Tool.trim("/", i.replace('\\', '/'), "/")).collect(Collectors.joining("/"));
        return Tool.of(Thread.currentThread().getContextClassLoader().getResource(path));
    }

    /**
     * @param relativePath relative path
     * @return url
     */
    public static Stream<URL> toURLs(String... relativePath) {
        String path = Stream.of(relativePath).map(i -> Tool.trim("/", i.replace('\\', '/'), "/")).collect(Collectors.joining("/"));
        return Tool.stream(Try.f(Thread.currentThread().getContextClassLoader()::getResources).apply(path));
    }

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
     * @param <T> value type
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
        return Tool.of(value);
    }

    /**
     * get optional
     * 
     * @param <T> type
     *
     * @param value value
     * @return optional
     */
    public static <T> Optional<T> of(T value) {
        return Optional.ofNullable(value);
    }

    /**
     * get non-empty and non-null string
     *
     * @param value value
     * @return String or empty if null or empty
     */
    public static Optional<String> string(Object value) {
        return optional(value, String::valueOf).filter(notEmpty);
    }

    /**
     * get integer
     *
     * @param value value
     * @return integer
     */
    public static Optional<Integer> integer(Object value) {
        return optional(value, Integer::parseInt);
    }

    /**
     * get long integer
     *
     * @param value value
     * @return long integer
     */
    public static Optional<Long> longInteger(Object value) {
        return optional(value, Long::parseLong);
    }

    /**
     * get optional
     * 
     * @param <T> object type
     * @param <R> result type
     *
     * @param object object
     * @param fromString string to value
     * @return optional value
     */
    public static <T, R> Optional<R> optional(T object, Function<String, R> fromString) {
        try {
            if (object != null) {
                String text = object.toString();
                if (text != null) {
                    return Tool.of(fromString.apply(text));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * @param <T> value types
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
        if (o instanceof Iterable) {
            StringBuilder s = new StringBuilder();
            String separator = "\n,\n";
            for (Object i : (Iterable<?>) o) {
                s.append(separator).append(dump(i));
            }
            return "[\n" + s.substring(separator.length()) + "\n]";
        }
        return Stream.of(o.getClass().getDeclaredFields()).map(field -> {
            String value = "null";
            try {
                field.setAccessible(true);
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
                throw new InternalError(e);
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
                    Try.triC(replacer).accept(writer, tag, prefix);
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
     * @param file file
     * @return true if text contents file
     */
    public static boolean isTextContent(String file) {
        String lower = file.toLowerCase();
        return Sys.text_extensions.stream().anyMatch(lower::endsWith);
    }

    /**
     * @param text text
     * @param prefix prefix
     * @return text
     */
    public static String prefix(String text, String prefix) {
        return text.startsWith(prefix) ? text : prefix + text;
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
     * @param <T> value type
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
        return getResources(packageName.replace('.', '/')).filter(s -> s.endsWith(".class")).map(f -> prefix + f.substring(0, f.length() - ".class".length()))
                .<Class<?>>map(Try.f(Class::forName));
    }

    /**
     * list files
     *
     * @param location location
     * @return file name stream(must to close)
     */
    public static Stream<String> getResources(String location) {
        return Tool.toURLs(location).flatMap(Try.f(i -> {
            boolean isDirectory = Try.<URL>p(j -> new File(j.getFile()).isDirectory(), (e, j) -> false).test(i);
            if ("jar".equals(i.getProtocol())) {
                return getResourcesFromJar(location, ((JarURLConnection) i.openConnection()).getJarFile());
            } else if (isDirectory) {
                return getResourcesFromFolder(new File(i.getFile()));
            } else {
                return getResourcesFromJar("", new JarFile(i.getFile()));
            }
        }));
    }

    /**
     * list files in jar
     *
     * @param location location
     * @param jar jar file
     * @return file name stream(must to close)
     */
    private static Stream<String> getResourcesFromJar(String location, JarFile jar) {
        return jar.stream().onClose(Try.r(jar::close)).map(JarEntry::getName).filter(i -> i.startsWith(location))
                .map(i -> trim("/", i.substring(location.length()), null));
    }

    /**
     * utf8 decoder
     */
    static final CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder().onUnmappableCharacter(CodingErrorAction.REPLACE)
            .onMalformedInput(CodingErrorAction.REPLACE);

    /**
     * list files in folder
     *
     * @param folder folder
     * @return file name stream(must to close)
     */
    private static Stream<String> getResourcesFromFolder(File folder) {
        try {
            return Files.list(folder.toPath()).map(Path::getFileName).map(Object::toString);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * InputStream to text
     *
     * @param in input
     * @return text
     */
    public static String loadText(InputStream in) {
        StringBuilder result = new StringBuilder();
        try (Reader reader = newReader(in)) {
            char[] buffer = new char[1024];
            for (;;) {
                int n = reader.read(buffer);
                if (n < 0) {
                    break;
                }
                result.append(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result.toString();
    }

    /**
     * @param in input
     * @return lines
     */
    static Stream<String> lines(InputStream in) {
        BufferedReader reader = new BufferedReader(newReader(in));
        return StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        return false;
                    }
                    action.accept(line);
                    return true;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return NONNULL | ORDERED;
            }

        }, false).onClose(Try.r(reader::close));
    }

    /**
     * create reader
     * 
     * @param in input
     * @return reader
     */
    public static Reader newReader(InputStream in) {
        return new InputStreamReader(in, utf8);
    }

    /**
     * build map{String: Object}
     * 
     * @param <T> value type
     *
     * @param keyValues key, value, key, value...
     * @return map
     */
    @SafeVarargs
    public static <T> Map<String, T> jsonMap(T... keyValues) {
        if (keyValues == null) {
            return null;
        }
        Map<String, T> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    /**
     * @param <K> key type
     * @param <V> value type
     * @param key key
     * @param value value
     * @param keyValues key, value,...
     * @return map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> map(K key, V value, Object... keyValues) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(key, value);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }

    /**
     * @param <K> key type
     * @param <V> value type
     * @return map
     */
    public static <K, V> Map<K, V> map() {
        return new LinkedHashMap<>();
    }

    /**
     * @param <K> key type
     * @param <V> value type
     * @param keyValues key, value,...
     * @return map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> map(Object[] keyValues) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }

    /**
     * @param text Text
     * @param separator Separator
     * @param pairSeparator Pair separator
     * @return Map
     */
    public static Map<String, String> parseMap(String text, String separator, String pairSeparator) {
        return string(text).map(s -> Stream.of(s.split(separator)).collect(() -> (Map<String, String>) new LinkedHashMap<String, String>(),
                (map, ss) -> peek(s.split(pairSeparator, 2), a -> map.put(a[0], a[1])), (a, b) -> a.putAll(b))).orElseGet(Collections::emptyMap);
    }

    /**
     * JSON Mapper
     */
    @SuppressWarnings("rawtypes")
    static ObjectMapper jsonMapper = Tool.peek(new ObjectMapper(), mapper -> {
        mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.NON_PRIVATE);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.registerModule(new SimpleModule().addSerializer(Optional.class, new JsonSerializer<Optional>() {
            @SuppressWarnings("unchecked")
            @Override
            public void serialize(Optional value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeObject(value.orElse(null));
            }
        }).addSerializer(OptionalInt.class, new JsonSerializer<OptionalInt>() {
            @Override
            public void serialize(OptionalInt value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeObject(value.orElse(0));
            }
        }).addSerializer(OptionalLong.class, new JsonSerializer<OptionalLong>() {
            @Override
            public void serialize(OptionalLong value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeObject(value.orElse(0));
            }
        }).addSerializer(OptionalDouble.class, new JsonSerializer<OptionalDouble>() {
            @Override
            public void serialize(OptionalDouble value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeObject(value.orElse(0));
            }
        }).addSerializer(Stream.class, new JsonSerializer<Stream>() {
            @SuppressWarnings("unchecked")
            @Override
            public void serialize(Stream value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeStartArray();
                value.forEachOrdered(Try.c(json::writeObject));
                json.writeEndArray();
            }
        }).addSerializer(IntStream.class, new JsonSerializer<IntStream>() {
            @Override
            public void serialize(IntStream value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeStartArray();
                value.forEachOrdered(Try.intC(json::writeNumber));
                json.writeEndArray();
            }
        }).addSerializer(LongStream.class, new JsonSerializer<LongStream>() {
            @Override
            public void serialize(LongStream value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeStartArray();
                value.forEachOrdered(Try.longC(json::writeNumber));
                json.writeEndArray();
            }
        }).addSerializer(DoubleStream.class, new JsonSerializer<DoubleStream>() {
            @Override
            public void serialize(DoubleStream value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                json.writeStartArray();
                value.forEachOrdered(Try.doubleC(json::writeNumber));
                json.writeEndArray();
            }
        }).addSerializer(Temporal.class, new JsonSerializer<Temporal>() {
            @Override
            public void serialize(Temporal value, JsonGenerator json, SerializerProvider provider) throws IOException, JsonProcessingException {
                if (value == null) {
                    json.writeNull();
                } else {
                    json.writeString(value.toString());
                }
            }
        }));
    });

    /**
     * XML Mapper
     */
    static XmlMapper xmlMapper = new XmlMapper();

    /**
     * @param o object
     * @return JSON
     */
    public static String json(Object o) {
        return Try.f(jsonMapper::writeValueAsString).apply(o);
    }

    /**
     * @param o object
     * @param out OutputStream
     */
    public static void json(Object o, OutputStream out) {
        Try.<OutputStream, Object>biC(jsonMapper::writeValue).accept(out, o);
    }

    /**
     * @param o object
     * @return XML
     */
    public static String xml(Object o) {
        return Try.f(xmlMapper::writeValueAsString).apply(o);
    }

    /**
     * @param o object
     * @param out OutputStream
     */
    public static void xml(Object o, OutputStream out) {
        Try.<OutputStream, Object>biC(xmlMapper::writeValue).accept(out, o);
    }

    /**
     * @param <T> first stream type
     * @param <U> second stream type
     * @param left first stream
     * @param right second stream
     * @return zipped stream
     */
    public static <T, U> Stream<Tuple<T, U>> zip(Stream<T> left, Stream<U> right) {
        Iterator<T> l = left.iterator();
        Iterator<U> r = right.iterator();
        Iterator<Tuple<T, U>> iterator = new Iterator<Tuple<T, U>>() {
            @Override
            public boolean hasNext() {
                return l.hasNext() && r.hasNext();
            }

            @Override
            public Tuple<T, U> next() {
                return Tuple.of(l.next(), r.next());
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL | Spliterator.ORDERED), false);
    }

    /**
     * @param <T> first stream type
     * @param <U> second stream type
     * @param left first stream
     * @param right second stream
     * @return zipped stream
     */
    public static <T, U> Stream<Tuple<T, U>> zipLong(Stream<T> left, Stream<U> right) {
        Iterator<T> l = left.iterator();
        Iterator<U> r = right.iterator();
        Iterator<Tuple<T, U>> iterator = new Iterator<Tuple<T, U>>() {
            @Override
            public boolean hasNext() {
                return l.hasNext() || r.hasNext();
            }

            @Override
            public Tuple<T, U> next() {
                return Tuple.of(l.hasNext() ? l.next() : null, r.hasNext() ? r.next() : null);
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL | Spliterator.ORDERED), false);
    }

    /**
     * get next time
     *
     * @param text duration text
     * @param from start point
     * @return milliseconds
     */
    public static long nextMillis(final String text, final ZonedDateTime from) {
        Objects.requireNonNull(from);
        if (Objects.requireNonNull(text).length() <= 0) {
            return 0;
        }
        String value = text.trim().toUpperCase();
        if ("DHMS".indexOf(value.charAt(value.length() - 1)) >= 0) {
            Duration interval = Duration.parse(value.endsWith("D") ? "P" + value : "PT" + value);
            return interval.toMillis();
        } else {
            ZonedDateTime next = from;
            int timeIndex = value.indexOf(':');
            if (timeIndex >= 0) {
                /* has time */
                while (timeIndex > 0) {
                    timeIndex--;
                    if ("0123456789".indexOf(value.charAt(timeIndex)) < 0) {
                        break;
                    }
                }
                List<ChronoField> fields = Arrays.asList(ChronoField.HOUR_OF_DAY, ChronoField.MINUTE_OF_HOUR, ChronoField.SECOND_OF_MINUTE);
                ChronoUnit[] units = { ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS };
                ChronoField[] field = { null, null };
                next = Tool
                        .zip(fields.stream(),
                                Stream.of(value.substring(timeIndex).trim().split("[^0-9]")).map(s -> Tool.string(s).map(Long::valueOf).orElse(-1L)))
                        .peek(i -> {
                            if (i.r < 0) {
                                field[0] = i.l;
                            } else {
                                field[1] = i.l;
                            }
                        }).reduce(next, (i, pair) -> pair.r < 0 ? i : i.with(pair.l, pair.r), (i, j) -> i).truncatedTo(units[fields.indexOf(field[1]) + 1]);
                if (from.isAfter(next)) {
                    next = next.plus(1, units[fields.indexOf(field[0]) + 1]);
                }
                value = value.substring(0, timeIndex).trim();
            } else {
                next = next.truncatedTo(ChronoUnit.DAYS);
            }
            if (!value.isEmpty()) {
                final ZonedDateTime start = next;
                boolean until = true;
                for (DayOfWeek i : DayOfWeek.values()) {
                    if (i.name().startsWith(value)) {
                        next = next.with(ChronoField.DAY_OF_WEEK, i.getValue());
                        if (start.isAfter(next)) {
                            next = next.plus(7, ChronoUnit.DAYS);
                        }
                        until = false;
                        break;
                    }
                }
                if (until) {
                    List<ChronoField> fields = Arrays.asList(ChronoField.DAY_OF_MONTH, ChronoField.MONTH_OF_YEAR, ChronoField.YEAR);
                    ChronoField[] field = { null };
                    ChronoUnit[] units = { ChronoUnit.MONTHS, ChronoUnit.YEARS, null };
                    List<String> values = Arrays.asList(value.split("[^0-9]"));
                    Collections.reverse(values);
                    next = Tool.zip(fields.stream(), values.stream().map(Long::valueOf)).peek(i -> field[0] = i.l).reduce(next,
                            (i, pair) -> i.with(pair.l, pair.r), (i, j) -> i);
                    if (start.isAfter(next)) {
                        next = next.plus(1, units[fields.indexOf(field[0])]);
                    }
                }
            }
            return ChronoUnit.MILLIS.between(from, next);
        }
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
                if (i > 0 && !Character.isUpperCase(text.charAt(i - 1))) {
                    result.append('_');
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

    /**
     * @param bytes bytes
     * @param algorithm algorithm
     * @return digest
     */
    public static byte[] digest(byte[] bytes, String algorithm) {
        MessageDigest digest = Try.s(() -> MessageDigest.getInstance(algorithm)).get();
        digest.update(bytes);
        return digest.digest();
    }

    /**
     * @param text text
     * @param algorithm algorithm
     * @return hash
     */
    public static String hash(String text, String algorithm) {
        return DatatypeConverter.printHexBinary(digest(text.getBytes(StandardCharsets.UTF_8), algorithm));
    }

    /**
     * @param text text
     * @return sha256
     */
    public static String hash(String text) {
        return hash(text, "SHA-256");
    }

    /**
     * copy stream
     * 
     * @param in input
     * @param out output
     * @param buffer buffer
     */
    public static void copy(InputStream in, OutputStream out, byte[] buffer) {
        for (;;) {
            try {
                int n = in.read(buffer);
                if (n <= 0) {
                    return;
                }
                out.write(buffer, 0, n);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Initial Vector
     */
    static final String IV = "CYKJRWWIYWJHSLEU";

    /**
     * encrypt stream
     * 
     * @param out output
     * @param key salt
     * @param iv initial vector(16characters=128bit)
     * @return OutputStream
     */
    public static OutputStream withEncrypt(OutputStream out, String key, String... iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/PCBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digest(key.getBytes(StandardCharsets.UTF_8), "MD5"), "AES"),
                    new IvParameterSpec((iv.length > 0 ? iv[0] : IV).getBytes(StandardCharsets.UTF_8)));
            return new CipherOutputStream(out, cipher);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param text text
     * @param key slat
     * @param iv initial vector
     * @return encrypted text
     */
    public static String encrypt(String text, String key, String... iv) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = withEncrypt(bytes, key, iv)) { // must to close before decrypt
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return DatatypeConverter.printHexBinary(bytes.toByteArray());
    }

    /**
     * decrypt stream
     * 
     * @param in input
     * @param key salt
     * @param iv initial vector(16characters=128bit)
     * @return InputStream
     */
    public static InputStream withDecrypt(InputStream in, String key, String... iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/PCBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(digest(key.getBytes(StandardCharsets.UTF_8), "MD5"), "AES"),
                    new IvParameterSpec((iv.length > 0 ? iv[0] : IV).getBytes(StandardCharsets.UTF_8)));
            return new CipherInputStream(in, cipher);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param text encrypted text
     * @param key slat
     * @param iv initial vector
     * @return decrypted text
     */
    public static String decrypt(String text, String key, String... iv) {
        return loadText(withDecrypt(new InputStream() {
            int max = text.length();
            int index = 0;

            @Override
            public int read() throws IOException {
                index += 2;
                if (index > max) {
                    return -1;
                }
                return Integer.parseInt(text.substring(index - 2, index), 16);
            }
        }, key, iv));
    }

    /**
     * @param map map
     * @param key key
     * @param value value
     * @return map
     */
    public static Map<String, List<String>> add(Map<String, List<String>> map, String key, String value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            List<String> list = new ArrayList<>();
            list.add(value);
            map.put(key, list);
        }
        return map;
    }

    /**
     * @param text text
     * @return Url encoded text
     */
    public static String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e);
        }
    }

    /**
     * @param text Url encoded text
     * @return text
     */
    public static String urlDecode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e);
        }
    }

    /**
     * @param text text
     * @return Base64 encoded text
     */
    public static String base64Encode(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param text Base64 encoded text
     * @return text
     */
    public static String base64Decode(String text) {
        try {
            return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    /**
     * base 128 characters
     */
    static final byte[] base128 = Try
            .s(() -> "ｦｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ!#$%&()*+-/0123456789:;?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]_abcdefghijklmnopqrstuvwxyz{}"
                    .getBytes("MS932"))
            .get();

    /**
     * @param out output
     * @return base128 OutputStream
     */
    public static OutputStream withBase128Encode(OutputStream out) {
        return new OutputStream() {
            byte[] buffer = new byte[7];
            int length = buffer.length;
            int position = 0;

            @Override
            public void close() throws IOException {
                if (position > 0) {
                    out.write(base128[buffer[0] & 0x7f]);
                }
                if (position > 0) {
                    out.write(base128[((buffer[0] >> 7) & 0x01) | ((buffer[1] << 1) & 0x7f)]);
                }
                if (position > 1) {
                    out.write(base128[((buffer[1] >> 6) & 0x03) | ((buffer[2] << 2) & 0x7f)]);
                }
                if (position > 2) {
                    out.write(base128[((buffer[2] >> 5) & 0x07) | ((buffer[3] << 3) & 0x7f)]);
                }
                if (position > 3) {
                    out.write(base128[((buffer[3] >> 4) & 0x0f) | ((buffer[4] << 4) & 0x7f)]);
                }
                if (position > 4) {
                    out.write(base128[((buffer[4] >> 3) & 0x1f) | ((buffer[5] << 5) & 0x7f)]);
                }
                if (position > 5) {
                    out.write(base128[((buffer[5] >> 2) & 0x3f) | ((buffer[6] << 6) & 0x7f)]);
                }
                if (position > 6) {
                    out.write(base128[(buffer[6] >> 1) & 0x7f]);
                }
                position = 0;
            }

            @Override
            public void write(int b) throws IOException {
                if (position < length) {
                    buffer[position] = (byte) b;
                    position++;
                }
                if (position < length) {
                    return;
                }
                close();
            }
        };
    }

    /**
     * @param text text
     * @return base128 encoded text
     */
    public static String base128Encode(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = withBase128Encode(bytes)) { // must to close before encoded
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return bytes.toString("MS932");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param in input
     * @return base128 InputStream
     */
    public static InputStream withBase128Decode(InputStream in) {
        return new InputStream() {
            byte[] buffer = new byte[8];
            int length = buffer.length;
            int position = 0;
            int max;

            @Override
            public int read() throws IOException {
                if (position == 0) {
                    max = in.read(buffer, 0, length);
                    if (max < 0) {
                        return -1;
                    }
                    for (int i = 0; i < max; i++) {
                        int b = Arrays.binarySearch(base128, buffer[i]);
                        if (b == -1) {
                            throw new IllegalArgumentException(
                                    "Invalid base128 character: " + new String(buffer, i, i + 1, "MS932") + String.format(" (0x%02X)", buffer[i]));
                        }
                        buffer[i] = (byte) b;
                    }
                }
                switch (position++) {
                case 0:
                    return max < 2 ? -1 : ((buffer[0] & 0xff) >> 0) | ((buffer[1] & 0x01) << 7);
                case 1:
                    return max < 3 ? -1 : ((buffer[1] & 0xfe) >> 1) | ((buffer[2] & 0x03) << 6);
                case 2:
                    return max < 4 ? -1 : ((buffer[2] & 0xfc) >> 2) | ((buffer[3] & 0x07) << 5);
                case 3:
                    return max < 5 ? -1 : ((buffer[3] & 0xf8) >> 3) | ((buffer[4] & 0x0f) << 4);
                case 4:
                    return max < 6 ? -1 : ((buffer[4] & 0xf0) >> 4) | ((buffer[5] & 0x1f) << 3);
                case 5:
                    return max < 7 ? -1 : ((buffer[5] & 0xe0) >> 5) | ((buffer[6] & 0x3f) << 2);
                default:
                    position = 0;
                    return max < 8 ? -1 : ((buffer[6] & 0xc0) >> 6) | ((buffer[7] & 0x7f) << 1);
                }
            }
        };
    }

    /**
     * @param text base128 encoded text
     * @return text
     */
    public static String base128Decode(String text) {
        try {
            return loadText(withBase128Decode(new ByteArrayInputStream(text.getBytes("MS932"))));
        } catch (IllegalArgumentException e) {
            return text;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * test
     *
     * @param args text
     */
    public static void main(String[] args) {
        // Stream.of(null, "", "Abc", "abcDef", "AbcDefG", "URLEncoder").map(Tool::camelToSnake).forEach(Log::info);
        // Stream.of(null, "", "abc", "abc___def_", "_abc_def_").map(Tool::snakeToCamel).forEach(Log::info);
        // // Stream.concat(Stream.of("1d", "2h", "3m", "4s", "1", "1/1", "12:00", "01:02:03"), Stream.of(args)).forEach(text -> Tool.nextMillis(text,
        // // ZonedDateTime.now()));
        // try(Stream<String> list = getResources("app/controller")) {
        // list.forEach(Log::info);
        // }
        // try (Stream<Class<?>> list = getClasses("test")) {
        // list.forEach(c -> Log.info(c.getCanonicalName()));
        // }
        String text = "target text!";
        // String password = "abcd123";
        // Log.info("source: " + text);
        // ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // try (OutputStream out = withEncrypt(bytes, password)) { // must to close before decrypt
        // out.write(text.getBytes(StandardCharsets.UTF_8));
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // byte[] encrypted = bytes.toByteArray();
        // Log.info("encrypted: " + hex(encrypted) + " / " + encrypt(text, password));
        // Log.info("decrypted: " + loadText(withDecrypt(new ByteArrayInputStream(encrypted), password)) + " / " + decrypt(encrypt(text, password),
        // password));
        Log.info("base128encoded: " + base128Decode(text));
        Log.info("base128decoded: " + base128Decode(base128Encode(text)));
        Log.info(Locale.getDefault().toString());
        Log.info(camelToSnake("LoginURL"));
    }

    /**
     * @param <T> return type
     * @param methodFullName full method name
     * @param argClasses argument classes
     * @param args arguments
     * @return return value
     * @throws NoSuchMethodException method not found
     * @throws IllegalAccessException access failed
     * @throws IllegalArgumentException illegal argument
     * @throws InvocationTargetException not throw
     * @throws SecurityException security error
     * @throws ClassNotFoundException class not found
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodFullName, Class<?>[] argClasses, Object... args) throws NoSuchMethodException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, SecurityException, ClassNotFoundException {
        int i = methodFullName.lastIndexOf('.');
        if (i < 0) {
            throw new NoSuchMethodException(methodFullName);
        }
        return (T) Class.forName(methodFullName.substring(0, i)).getDeclaredMethod(methodFullName.substring(i + 1), argClasses).invoke(null, args);
    }

    /**
     * @param <T> return type
     * @param instance instance
     * @param name method name
     * @param argClasses argument classes
     * @param args arguments
     * @return return value
     * @throws IllegalAccessException access failed
     * @throws IllegalArgumentException illegal argument
     * @throws InvocationTargetException not throw
     * @throws NoSuchMethodException method not found
     * @throws SecurityException security error
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object instance, String name, Class<?>[] argClasses, Object... args)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        return (T) instance.getClass().getDeclaredMethod(name, argClasses).invoke(instance, args);
    }

    /**
     * @param <T> return type
     * @param value optional value
     * @param ifPresent action if present
     * @param ifNotPresent action if not present
     * @return true if present
     */
    public static <T> boolean ifPresentOr(Optional<T> value, Consumer<T> ifPresent, Runnable ifNotPresent) {
        value.ifPresent(ifPresent);
        if (!value.isPresent()) {
            ifNotPresent.run();
            return false;
        }
        return true;
    }

    /**
     * @param <T> Object type
     * @param consumer ObjIntConsumer
     * @return Consumer
     */
    public static <T> Consumer<T> withIndex(ObjIntConsumer<T> consumer) {
        AtomicInteger n = new AtomicInteger(-1);
        return obj -> consumer.accept(obj, n.incrementAndGet());
    }

    /**
     * @param <T> value type
     * @param value value
     * @param action action(not call if value is null)
     */
    public static <T> void let(T value, Consumer<T> action) {
        if (value != null) {
            action.accept(value);
        }
    }

    /**
     * @param <T> value type
     * @param <R> return type
     * @param value value
     * @param converter call if value is not null
     * @return value
     */
    public static <T, R> R val(T value, Function<T, R> converter) {
        return converter.apply(value);
    }

    /**
     * @param <T> value type
     * @param value value
     * @param consumer consumer
     * @return value
     */
    public static <T> T peek(T value, Consumer<T> consumer) {
        if (consumer != null) {
            consumer.accept(value);
        }
        return value;
    }

    /**
     * @param text text
     * @param separator separator(regex)
     * @param index index(allow negative)
     * @return part text
     */
    public static String splitAt(String text, String separator, int index) {
        if (!string(text).isPresent()) {
            return text;
        }
        String[] parts = text.split(separator);
        return parts[index < 0 ? parts.length + index : index];
    }

    /**
     * @param map map
     * @param name name
     * @param value value
     * @return values
     */
    public static <T, U> List<U> addValue(Map<T, List<U>> map, T name, U value) {
        List<U> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        list.add(value);
        return list;
    }

    /**
     * @param map map
     * @param name name
     * @param value value
     * @return self
     */
    public static <T, U> List<U> setValue(Map<T, List<U>> map, T name, U value) {
        List<U> list = new ArrayList<>();
        list.add(value);
        map.put(name, list);
        return list;
    }

    /**
     * @param map map
     * @param name name
     * @return first value
     */
    public static <T, U> Optional<U> getFirst(Map<T, List<U>> map, T name) {
        return Tool.of(map).map(m -> m.get(name)).filter(a -> !a.isEmpty()).map(a -> a.get(0));
    }

    /**
     * @param text text
     * @param max max length
     * @return cut text
     */
    public static String cut(String text, int max) {
        return Tool.of(text).map(i -> {
            boolean suffix = false;
            int index = i.indexOf('\r');
            if (index < 0) {
                index = i.indexOf('\n');
            }
            if (index >= 0) {
                i = i.substring(0, index);
                suffix = true;
            }
            if (i.length() > max) {
                i = i.substring(0, max);
                suffix = true;
            }
            return i + (suffix ? " ..." : "");
        }).orElse(null);
    }

    /**
     * @param path path
     * @return folder name(with end separator)
     */
    public static String getFolder(String path) {
        if (path == null) {
            return null;
        }
        int end = path.lastIndexOf('/');
        if (end < 0) {
            end = path.lastIndexOf('\\');
            if (end < 0) {
                end = 0;
            }
        }
        return path.substring(0, end);
    }

    /**
     * @param path path
     * @return file name(without extension)
     */
    public static String getName(String path) {
        if (path == null) {
            return null;
        }
        int start = path.lastIndexOf('/');
        if (start < 0) {
            start = path.lastIndexOf('\\');
            if (start < 0) {
                start = 0;
            }
        }
        int end = path.lastIndexOf('.');
        if (end < 0 || start > end) {
            end = path.length();
        }
        return path.substring(start, end);
    }

    /**
     * @param path path
     * @return extension
     */
    public static String getExtension(String path) {
        if (path == null) {
            return null;
        }
        int index = path.lastIndexOf('.');
        if (index < 0 || path.lastIndexOf('/') > index || path.lastIndexOf('\\') > index) {
            return "";
        }
        return path.substring(index);
    }

    /**
     * @param text text
     * @return Character array
     */
    public static Character[] toCharacterArray(String text) {
        return text.chars().mapToObj(j -> Character.valueOf((char) j)).toArray(Character[]::new);
    }

    /**
     * DateTimeFormatter toString cache
     */
    public static final Map<String, String> formatCache = new ConcurrentHashMap<>();

    /**
     * @param pattern pattern
     * @return DateTimeFormatter
     */
    public static DateTimeFormatter getFormat(String pattern) {
        return peek(DateTimeFormatter.ofPattern(pattern), format -> formatCache.computeIfAbsent(format.toString(), key -> pattern));
    }

    /**
     * Mail settings
     */
    public static class Mail {
        static {
            System.setProperty("sun.nio.cs.map", "x-windows-iso2022jp/ISO-2022-JP");
        }
        /**
         * Users
         */
        protected final Map<RecipientType, InternetAddress[]> users = new LinkedHashMap<>();

        /**
         * Properties
         */
        protected final Properties properties = new Properties();
        {
            properties.put("mail.smtp.host", Sys.Mail.host);
            properties.put("mail.smtp.port", Sys.Mail.port);
            properties.put("mail.smtp.auth", Sys.Mail.auth);
            properties.put("mail.smtp.starttls.enable", Sys.Mail.startTls);
            properties.put("mail.smtp.connectiontimeout", Sys.Mail.connectionTimeout);
            properties.put("mail.smtp.timeout", Sys.Mail.readTimeout);
            properties.put("mail.user", Sys.Mail.user);
            properties.put("mail.host", Sys.Mail.host);
            properties.put("mail.debug", Sys.Mail.debug);
        }

        /**
         * From address
         */
        protected InternetAddress from;

        /**
         * Reply to address
         */
        protected InternetAddress[] replyTo;

        /**
         * Authenticator
         */
        protected Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(Sys.Mail.user, Sys.Mail.password);
            }
        };

        /**
         * Constructor
         */
        protected Mail() {
        }

        /**
         * @param type TO or CC or BCC
         * @param addresses E-mail address
         * @return Self
         */
        public Mail address(RecipientType type, String... addresses) {
            users.put(type, Stream.of(addresses).map(Try.f(InternetAddress::new)).toArray(InternetAddress[]::new));
            return this;
        }

        /**
         * @param type TO or CC or BCC
         * @param addressNames pair of E-mail address and display name
         * @return Self
         */
        public Mail addressWithName(RecipientType type, String... addressNames) {
            users.put(type, parse(addressNames));
            return this;
        }

        /**
         * @param addressNames pair of E-mail address and display name
         * @return Array of InternetAddress
         */
        static InternetAddress[] parse(String... addressNames) {
            return IntStream.range(0, addressNames.length / 2).map(i -> i * 2)
                    .<InternetAddress>mapToObj(Try.intF(i -> new InternetAddress(addressNames[i], addressNames[i + 1], Sys.Mail.charset)))
                    .toArray(InternetAddress[]::new);
        }

        /**
         * @param authenticator Authenticator
         * @return Self
         */
        public Mail authenticator(Authenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        /**
         * @param from From e-mail address
         * @param name Display name
         * @return Self
         */
        public Mail from(String from, String name) {
            this.from = Try.s(() -> new InternetAddress(from, name, Sys.Mail.charset)).get();
            return this;
        }

        /**
         * @param addressNames pair of E-mail address and display name
         * @return Self
         */
        public Mail replyTo(String... addressNames) {
            this.replyTo = parse(addressNames);
            return this;
        }

        /**
         * @param addressNames pair of E-mail address and display name
         * @return Self
         */
        public Mail toWithName(String... addressNames) {
            return addressWithName(RecipientType.TO, addressNames);
        }

        /**
         * @param addresses Array of E-mail address
         * @return Self
         */
        public Mail to(String... addresses) {
            return address(RecipientType.TO, addresses);
        }

        /**
         * @param addressNames pair of E-mail address and display name
         * @return Self
         */
        public Mail ccWithName(String... addressNames) {
            return addressWithName(RecipientType.CC, addressNames);
        }

        /**
         * @param addresses Array of E-mail address
         * @return Self
         */
        public Mail cc(String... addresses) {
            return address(RecipientType.CC, addresses);
        }

        /**
         * @param addressNames pair of E-mail address and display name
         * @return Self
         */
        public Mail bccWithName(String... addressNames) {
            return addressWithName(RecipientType.BCC, addressNames);
        }

        /**
         * @param addresses Array of E-mail address
         * @return Self
         */
        public Mail bcc(String... addresses) {
            return address(RecipientType.BCC, addresses);
        }
    }

    /**
     * @param subject Mail subject
     * @param body Mail body
     * @param to To e-mail address
     */
    public static void mail(String subject, String body, String... to) {
        mail(subject, body, mail -> mail.to(to));
    }

    /**
     * @param subject Mail subject
     * @param body Mail body
     * @param set setter
     */
    public static void mail(String subject, String body, Consumer<Mail> set) {
        Mail mail = new Mail();
        set.accept(mail);
        MimeMessage message = new MimeMessage(javax.mail.Session.getInstance(mail.properties, mail.authenticator));
        InternetAddress from = Tool.of(mail.from).orElseGet(Try.s(() -> new InternetAddress(Sys.Mail.user)));
        try {
            message.setFrom(from);
            message.setReplyTo(Tool.array(from));
            mail.users.forEach(Try.biC((type, users) -> message.setRecipients(type, users)));
            message.setSubject(subject, Sys.Mail.charset);
            message.setText(body, Sys.Mail.charset);
            message.setHeader("Content-Transfer-Encoding", Sys.Mail.encoding);
            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
