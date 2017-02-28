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
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.activation.MimetypesFileTypeMap;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
     * Carriage Return
     */
    public static final String CR = "\r";

    /**
     * Line Feed
     */
    public static final String LF = "\n";

    /**
     * Carriage Return and Line Feed
     */
    public static final String CRLF = CR + LF;

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
     * @param file file
     * @return true if text contents file
     */
    public static boolean isTextContent(String file) {
        String lower = file.toLowerCase();
        return Config.app_text_extensions.stream().anyMatch(lower::endsWith);
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
        return Config.toURL(location).map(Try.f(i -> {
            boolean isDirectory = Try.<URL>p(j -> new File(j.getFile()).isDirectory(), (e, j) -> false).test(i);
            if ("jar".equals(i.getProtocol())) {
                Logger.getGlobal().info("jar inside");
                return getResourcesFromJar(location, ((JarURLConnection) i.openConnection()).getJarFile());
            } else if (isDirectory) {
                Logger.getGlobal().info("folder");
                return getResourcesFromFolder(location, new File(i.getFile()));
            } else {
                Logger.getGlobal().info("jar file");
                return getResourcesFromJar("", new JarFile(i.getFile()));
            }
        })).orElse(Stream.empty());
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
     * @param location location
     * @param folder folder
     * @return file name stream(must to close)
     */
    private static Stream<String> getResourcesFromFolder(String location, File folder) {
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
     * @return sha256
     */
    public static String hash(String text) {
        return hex(digest(text.getBytes(StandardCharsets.UTF_8), "SHA-256"));
    }

    /**
     * @param bytes bytes
     * @return hex string
     */
    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
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
        return hex(bytes.toByteArray());
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
     * @return logger
     */
    public static Logger getLogger() {
        return Logger.getLogger(Request.current().map(i -> String.valueOf(i.hashCode())).orElse(""));
    }

    /**
     * @param map map
     * @param key key
     * @param value value
     */
    public static void add(Map<String, List<String>> map, String key, String value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            List<String> list = new ArrayList<>();
            list.add(value);
            map.put(key, list);
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
     * @return base128 text
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
                        if(b == -1) {
                            throw new IllegalArgumentException("Invalid base128 character: " + new String(buffer, i, i + 1, "MS932") + String.format(" (0x%02X)", buffer[i]));
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
     * @param text encoded text
     * @return decoded text
     */
    public static String base128Decode(String text) {
        try {
            return loadText(withBase128Decode(new ByteArrayInputStream(text.getBytes("MS932"))));
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
        // Stream.of(null, "", "Abc", "abcDef", "AbcDefG", "URLEncoder").map(Tool::camelToSnake).forEach(Logger.getGlobal()::info);
        // Stream.of(null, "", "abc", "abc___def_", "_abc_def_").map(Tool::snakeToCamel).forEach(Logger.getGlobal()::info);
        // // Stream.concat(Stream.of("1d", "2h", "3m", "4s", "1", "1/1", "12:00", "01:02:03"), Stream.of(args)).forEach(text -> Tool.nextMillis(text,
        // // ZonedDateTime.now()));
        // try(Stream<String> list = getResources("app/controller")) {
        // list.forEach(Logger.getGlobal()::info);
        // }
        // try (Stream<Class<?>> list = getClasses("test")) {
        // list.forEach(c -> Logger.getGlobal().info(c.getCanonicalName()));
        // }
        Logger logger = Logger.getGlobal();
        String text = "target text!";
        // String password = "abcd123";
        // logger.info("source: " + text);
        // ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // try (OutputStream out = withEncrypt(bytes, password)) { // must to close before decrypt
        // out.write(text.getBytes(StandardCharsets.UTF_8));
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // byte[] encrypted = bytes.toByteArray();
        // logger.info("encrypted: " + hex(encrypted) + " / " + encrypt(text, password));
        // logger.info("decrypted: " + loadText(withDecrypt(new ByteArrayInputStream(encrypted), password)) + " / " + decrypt(encrypt(text, password),
        // password));
        logger.info("base128encoded: " + base128Decode(text));
        logger.info("base128decoded: " + base128Decode(base128Encode(text)));
    }
}
