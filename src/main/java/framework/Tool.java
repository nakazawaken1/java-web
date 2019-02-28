package framework;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

import app.config.Sys;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Try.BiIntFunction;
import framework.Try.ObjIntFunction;
import framework.Try.TryFunction;
import framework.Try.TrySupplier;
import framework.Try.TryTriConsumer;
import framework.annotation.Help;
import framework.annotation.Stringer;

/**
 * Utility
 */
public class Tool {

    /**
     * not empty string
     */
    public static final Predicate<String> notEmpty = not(String::isEmpty);

    /**
     * @param <T> Value type
     * @param predicate Predicate
     * @return Negate predicate
     */
    public static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }

    /**
     * @param relativePath relative path
     * @return url
     */
    public static Optional<URL> toURL(String... relativePath) {
        String path = Stream.of(relativePath)
            .map(i -> trim("/", i.replace('\\', '/'), "/"))
            .collect(Collectors.joining("/"));
        return of(Thread.currentThread()
            .getContextClassLoader()
            .getResource(path));
    }

    /**
     * @param relativePath relative path
     * @return url
     */
    public static Stream<URL> toURLs(String... relativePath) {
        String path = Stream.of(relativePath)
            .map(i -> trim("/", i.replace('\\', '/'), "/"))
            .collect(Collectors.joining("/"));
        return stream(Try.f(Thread.currentThread()
            .getContextClassLoader()::getResources)
            .apply(path));
    }

    /**
     * @param url URL
     * @return true if directory
     */
    public static boolean isDirectory(URL url) {
        if (url == null) {
            return false;
        }
        try {
            if ("jar".equals(url.getProtocol())) {
                JarURLConnection c = (JarURLConnection) url.openConnection();
                return c.getJarEntry().isDirectory();
            } else {
                return new File(url.toURI()).isDirectory();
            }
        } catch (IOException | URISyntaxException e) {
            return false;
        }
    }

    /**
     * PrintStream to String
     *
     * @param action write to PrintStream
     * @return wrote text
     */
    public static String print(Consumer<PrintStream> action) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(out, false, StandardCharsets.UTF_8.name())) {
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
        return of(value);
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
    public static <T> Optional<T> or(Optional<T> value, Supplier<Optional<T>>... suppliers) {
        for (Supplier<Optional<T>> i : suppliers) {
            if (value.isPresent()) {
                break;
            }
            value = i.get();
        }
        return value;
    }

    /**
     * get optional
     * 
     * @param <T> type
     *
     * @return optional
     */
    public static <T> Optional<T> of() {
        return Optional.empty();
    }

    /**
     * get optional
     * 
     * @param <T> type
     *
     * @param value value
     * @return optional
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> of(T value) {
        return value instanceof Optional ? (Optional<T>) value : Optional.ofNullable(value);
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
        return value instanceof Number ? Optional.of(((Number) value).intValue()) : optional(value, Integer::parseInt);
    }

    /**
     * get long integer
     *
     * @param value value
     * @return long integer
     */
    public static Optional<Long> longInteger(Object value) {
        return value instanceof Number ? Optional.of(((Number) value).longValue()) : optional(value, Long::parseLong);
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
                    return of(fromString.apply(text));
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
     * @return is string
     */
    public static boolean isString(Object o) {
        if (o instanceof Optional) {
            o = ((Optional<?>) o).orElse(null);
        }
        if (o == null) {
            return false;
        }
        Class<?> c = o.getClass();
        return !((c.isPrimitive() && c != char.class) || o instanceof BigDecimal || o instanceof BigInteger || o instanceof Boolean || o instanceof Byte
                || o instanceof Double || o instanceof Float || o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Map
                || c.isArray() || o instanceof Iterable);
    }

    /**
     * @param c Class
     * @return True if array like class
     */
    public static boolean isSequence(Class<?> c) {
        return c != null && (c.isArray() || Stream.class.isAssignableFrom(c) || Iterable.class.isAssignableFrom(c));
    }

    /**
     * Handler for traverse
     */
    public interface Traverser extends Supplier<String> {

        /**
         * Call before traverse
         */
        default void prepare() {
        }

        /**
         * @param clazz Class
         */
        void start(Class<?> clazz);

        /**
         * @param key Key
         */
        void key(String key);

        /**
         * @param value Value
         * @param clazz Class
         * @param isString True if quoted
         */
        void value(String value, Class<?> clazz, boolean isString);

        /**
         * @param clazz Class
         */
        void end(Class<?> clazz);

        /**
         * @return If true, the Optional.empty field is not output
         */
        boolean isCompact();
    }

    /**
     * @param o Object
     * @param traverser Callback Handler
     * @param hashes Inner use only
     * @return Value
     */
    @SuppressWarnings("unchecked")
    @SafeVarargs
    public static String traverse(Object o, Traverser traverser, Set<Object>... hashes) {
        final boolean first = hashes.length <= 0;
        if (first) {
            traverser.prepare();
        }
        do {
            if (o == null || o == Optional.empty()) {
                traverser.value(null, null, false);
                break;
            }
            if (o instanceof Optional) {
                o = ((Optional<?>) o).get();
            }
            final Set<Object> cache = first ? new HashSet<>() : hashes[0];
            Class<?> c = o.getClass();
            if (o instanceof Tuple) {
                traverse(((Tuple<?, ?>) o).toList(), traverser, cache);
                break;
            }
            if (o instanceof Iterable) {
                traverser.start(c);
                for (Object i : (Iterable<?>) o) {
                    traverse(i, traverser, cache);
                }
                traverser.end(c);
                break;
            }
            if (o instanceof Stream) {
                traverser.start(c);
                ((Stream<?>) o).forEach(i -> traverse(i, traverser, cache));
                traverser.end(c);
                break;
            }
            if (o instanceof Map) {
                traverser.start(c);
                ((Map<?, ?>) o).forEach((k, v) -> {
                    traverser.key(String.valueOf(k));
                    traverse(v, traverser, cache);
                });
                traverser.end(c);
                break;
            }
            if (c.isArray()) {
                traverser.start(c);
                for (int i = 0, i2 = Array.getLength(o); i < i2; i++) {
                    traverse(Array.get(o, i), traverser, cache);
                }
                traverser.end(c);
                break;
            }
            if (o instanceof String || o instanceof Number || o instanceof Character || o instanceof Boolean
                    || o instanceof Date || o instanceof Temporal || o instanceof Message || o instanceof Xml
                    || o instanceof Tuple) {
                traverser.value(o.toString(), c, isString(o));
                break;
            }
            if (cache.contains(o)) {
                traverser.value("(loop)", c, true);
                break;
            }
            traverser.start(c);
            Object object = o;
            Reflector.fields(c).values().stream()
                .filter(f -> val(f.getModifiers(), m -> !Modifier.isPrivate(m) && !Modifier.isStatic(m)))
                .forEach(field -> {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(object);
                        boolean isOptional = value instanceof Optional;
                        if (isOptional && value == Optional.empty() && traverser.isCompact()) {
                            return;
                        }
                        traverser.key(of(field.getAnnotation(Help.class))
                            .map(help -> help.value()[0])
                            .orElse(field.getName()));
                        if (value != null) {
                            Stringer stringer = field.getAnnotation(Stringer.class);
                            if (stringer != null) {
                                Stringer.FromTo<Object> ft = (Stringer.FromTo<Object>) Reflector.instance(stringer.value());
                                ft.toString(value, traverser);
                            } else if (!isOptional && !isSequence(value.getClass()) && !(value instanceof Map) && Reflector.method(value.getClass(), "toString")
                                .map(Method::getDeclaringClass)
                                .filter(i -> i != Object.class)
                                .isPresent()) {
                                traverser.value(value.toString(), c, isString(value));
                                return;
                            } else {
                                if (isOptional) {
                                    value = ((Optional<Object>) value).orElse("");
                                }
                                traverse(value, traverser, cache);
                                cache.add(value);
                            }
                        } else {
                            traverser.value(null, c, false);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                        throw new InternalError(e);
                    }
                });
            traverser.end(c);
        } while (false);
        return first ? traverser.get() : null;
    }

    /**
     * @param o object
     * @return text
     */
    public static String json(Object o) {
        return traverse(o, new JsonTraverser());
    }

    /**
     * @param o Object
     * @param out Output
     * @param charset Charset
     */
    public static void json(Object o, OutputStream out, Charset charset) {
        traverse(o, peek(new JsonTraverser(), t -> {
            t.out = out;
            t.charset = charset;
        }));
    }

    /**
     * Json traverser
     */
    public static class JsonTraverser implements Traverser {
        /**
         * Key-value separator
         */
        public String separator = ": ";
        /**
         * Suffix
         */
        public String suffix = ",";
        /**
         * Newline
         */
        public String newline = Xml.newline;
        /**
         * Indent
         */
        public String indent = Xml.indent;
        /**
         * Buffer size
         */
        public int bufferSize = 1024 * 1024;
        /**
         * String closure
         */
        public char closure = '"';
        /**
         * Array start character
         */
        public char startArray = '[';
        /**
         * Array end character
         */
        public char endArray = ']';
        /**
         * Object start character
         */
        public char startObject = '{';
        /**
         * Object end character
         */
        public char endObject = '}';
        /**
         * If true, the Optional.empty field is not output
         */
        public boolean isCompact = true;
        /**
         * Output(return string if null)
         */
        public OutputStream out = null;
        /**
         * Charset(required if output is not null)
         */
        public Charset charset = null;

        /**
         * Buffer
         */
        private StringBuilder buffer = new StringBuilder();
        /**
         * Current indent
         */
        private StringBuilder currentIndent = new StringBuilder();
        /**
         * Separator length(cache)
         */
        private int separatorLength;
        /**
         * Suffix length(cache)
         */
        private int suffixLength;
        /**
         * True if first output
         */
        private boolean done = false;

        /**
         * Trim unnecessary suffix
         */
        void trim() {
            int length = buffer.length();
            if (buffer.subSequence(length - suffixLength, length)
                .toString()
                .equals(suffix)) {
                buffer.setLength(length - suffixLength);
            }
        }

        /**
         * Output prefix
         */
        void prefix() {
            if (done) {
                buffer.append(newline)
                    .append(currentIndent);
            } else {
                done = true;
            }
        }

        /**
         * Oputput prefix if necessary
         */
        void smartPrefix() {
            int length = buffer.length();
            if (buffer.length() <= separatorLength || !buffer.subSequence(length - separatorLength, length)
                .toString()
                .equals(separator)) {
                prefix();
            } else {
                done = true;
            }
        }

        /**
         * Flush buffer
         */
        void flush() {
            try {
                out.write(buffer.toString()
                    .getBytes(charset));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#prepare()
         */
        @Override
        public void prepare() {
            suffixLength = suffix.length();
            separatorLength = separator.length();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#start(java.lang.Class)
         */
        @Override
        public void start(Class<?> clazz) {
            smartPrefix();
            buffer.append(isSequence(clazz) ? startArray : startObject);
            currentIndent.append(indent);
            if (out != null && buffer.length() > bufferSize) {
                flush();
                buffer.setLength(0);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#key(java.lang.String)
         */
        @Override
        public void key(String key) {
            prefix();
            buffer.append(closure)
                .append(scriptEscape(key))
                .append(closure)
                .append(separator);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#value(java.lang.String, java.lang.Class, boolean)
         */
        @Override
        public void value(String value, Class<?> clazz, boolean isString) {
            if (isSequence(clazz)) {
                smartPrefix();
            } else {
                done = true;
            }
            if (isString) {
                buffer.append(closure)
                    .append(scriptEscape(value))
                    .append(closure)
                    .append(suffix);
            } else {
                buffer.append(value)
                    .append(suffix);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#end(java.lang.Class)
         */
        @Override
        public void end(Class<?> clazz) {
            currentIndent.setLength(currentIndent.length() - indent.length());
            trim();
            prefix();
            buffer.append(isSequence(clazz) ? endArray : endObject)
                .append(suffix);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.function.Supplier#get()
         */
        @Override
        public String get() {
            trim();
            if (out != null) {
                flush();
                return null;
            }
            return buffer.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#isCompact()
         */
        @Override
        public boolean isCompact() {
            return isCompact;
        }
    }

    /**
     * @param o object
     * @return text
     */
    public static String xml(Object o) {
        return traverse(o, new XmlTraverser());
    }

    /**
     * @param o Object
     * @param out Output
     * @param charset Charset
     */
    public static void xml(Object o, OutputStream out, Charset charset) {
        traverse(o, peek(new XmlTraverser(), t -> {
            t.out = out;
            t.charset = charset;
        }));
    }

    /**
     * Xml traverser
     */
    public static class XmlTraverser implements Traverser {
        /**
         * Newline
         */
        public String newline = Xml.newline;
        /**
         * Indent
         */
        public String indent = Xml.indent;
        /**
         * Buffer size
         */
        public int bufferSize = 1024 * 1024;
        /**
         * Array start character
         */
        public char prefix = '<';
        /**
         * Array end character
         */
        public char suffix = '>';
        /**
         * Object start character
         */
        public char endPrefix = '/';
        /**
         * Header output if true
         */
        public boolean hasHeader = true;
        /**
         * If true, the Optional.empty field is not output
         */
        public boolean isCompact = true;
        /**
         * Class name mapping
         */
        public final Map<String, String> classMap = map("Object", "root");
        /**
         * Class to tag name
         */
        public Function<Class<?>, String> classToTag = clazz -> val(of(clazz.getAnnotation(Help.class))
            .map(help -> help.value()[0])
            .orElseGet(clazz::getSimpleName), i -> classMap.getOrDefault(i, i));
        /**
         * Output(return string if null)
         */
        public OutputStream out = null;
        /**
         * Charset(required if output is not null)
         */
        public Charset charset = null;

        /**
         * Buffer
         */
        private StringBuilder buffer = new StringBuilder();
        /**
         * Current indent
         */
        private StringBuilder currentIndent = new StringBuilder();
        /**
         * Tag name stack
         */
        private Deque<String> tags = new LinkedList<>();

        /**
         * Flush buffer
         */
        void flush() {
            try {
                out.write(buffer.toString()
                    .getBytes(charset));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#prepare()
         */
        @Override
        public void prepare() {
            if (hasHeader) {
                buffer.append("<?xml version=\"1.0\" encoding=\"")
                    .append(charset.name())
                    .append("\"?>")
                    .append(newline);
            }
            buffer.append(prefix)
                .append(classToTag.apply(Object.class))
                .append(suffix);
            currentIndent.append(indent);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#start(java.lang.Class)
         */
        @Override
        public void start(Class<?> clazz) {
            if (!isSequence(clazz)) {
                buffer.append(newline)
                    .append(currentIndent)
                    .append(prefix)
                    .append(classToTag.apply(clazz))
                    .append(suffix);
                currentIndent.append(indent);
            }
            if (out != null && buffer.length() > bufferSize) {
                flush();
                buffer.setLength(0);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#key(java.lang.String)
         */
        @Override
        public void key(String key) {
            tags.push(key);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#value(java.lang.String, java.lang.Class, boolean)
         */
        @Override
        public void value(String value, Class<?> clazz, boolean isString) {
            String tag = tags.peek();
            buffer.append(newline)
                .append(currentIndent);
            if (tag != null) {
                buffer.append(prefix)
                    .append(tag)
                    .append(suffix);

            }
            buffer.append(value == null ? "" : value);
            if (tag != null) {
                buffer.append(prefix)
                    .append(endPrefix)
                    .append(tag)
                    .append(suffix);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#end(java.lang.Class)
         */
        @Override
        public void end(Class<?> clazz) {
            if (!isSequence(clazz)) {
                currentIndent.setLength(currentIndent.length() - indent.length());
                buffer.append(newline)
                    .append(currentIndent)
                    .append(prefix)
                    .append(endPrefix)
                    .append(classToTag.apply(clazz))
                    .append(suffix);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.function.Supplier#get()
         */
        @Override
        public String get() {
            buffer.append(newline)
                .append(prefix)
                .append(endPrefix)
                .append(classToTag.apply(Object.class))
                .append(suffix);
            if (out != null) {
                flush();
                return null;
            }
            return buffer.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#isCompact()
         */
        @Override
        public boolean isCompact() {
            return isCompact;
        }
    }

    /**
     * @param o object
     * @return text
     */
    public static String csv(Object o) {
        return traverse(o, new CsvTraverser());
    }

    /**
     * @param o Object
     * @param out Output
     * @param charset Charset
     */
    public static void csv(Object o, OutputStream out, Charset charset) {
        traverse(o, peek(new CsvTraverser(), t -> {
            t.out = out;
            t.charset = charset;
        }));
    }

    /**
     * @param o object
     * @return text
     */
    public static String tsv(Object o) {
        return traverse(o, peek(new CsvTraverser(), t -> {
            t.separator = '\t';
            t.clouser = '\0';/* none */
            t.innerSeparator = ",";
        }));
    }

    /**
     * @param o Object
     * @param out Output
     * @param charset Charset
     */
    public static void tsv(Object o, OutputStream out, Charset charset) {
        traverse(o, peek(new CsvTraverser(), t -> {
            t.out = out;
            t.charset = charset;
            t.separator = '\t';
            t.clouser = '\0';/* none */
            t.innerSeparator = ",";
        }));
    }

    /**
     * Tsv traverser
     */
    public static class CsvTraverser implements Traverser {
        /**
         * Newline
         */
        public String newline = Xml.newline;
        /**
         * Separator
         */
        public char separator = ',';
        /**
         * Array start character
         */
        public char clouser = '"';
        /**
         * Buffer size
         */
        public int bufferSize = 1024 * 1024;
        /**
         * Header output if true
         */
        public boolean hasHeader = true;
        /**
         * Nested value seprator
         */
        public String innerSeparator = ";";
        /**
         * Output(return string if null)
         */
        public OutputStream out = null;
        /**
         * Charset(required if output is not null)
         */
        public Charset charset = null;

        /**
         * Buffer
         */
        private StringBuilder buffer = new StringBuilder();
        /**
         * First line values
         */
        private List<String> values = new ArrayList<>();
        /**
         * Nested values
         */
        private List<String> nested = new ArrayList<>();
        /**
         * true if first column
         */
        private boolean firstColumn;
        /**
         * Nesting level
         */
        private int level = 0;
        /**
         * Max level
         */
        private int maxLevel = 0;

        /**
         * Flush buffer
         */
        void flush() {
            try {
                out.write(buffer.toString()
                    .getBytes(charset));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#start(java.lang.Class)
         */
        @Override
        public void start(Class<?> clazz) {
            level++;
            if (level > maxLevel) {
                maxLevel = level;
            }
            if (level == 1 || level == 2) {
                firstColumn = true;
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#key(java.lang.String)
         */
        @Override
        public void key(String key) {
            if (level > 2 || values == null || !hasHeader) {
                return;
            }
            if (!firstColumn) {
                buffer.append(separator);
            }
            if (clouser != '\0') {
                buffer.append(clouser)
                    .append(key)
                    .append(clouser);
            } else {
                buffer.append(key);
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#value(java.lang.String, java.lang.Class, boolean)
         */
        @Override
        public void value(String value, Class<?> clazz, boolean isString) {
            if (value == null) {
                value = "";
            }
            if (level == 0) {
                buffer.append(value);
                return;
            }
            if (level > 2) {
                nested.add(value);
                return;
            }
            if (values != null) {
                firstColumn = false;
                values.add(value);
            } else {
                if (firstColumn) {
                    firstColumn = false;
                } else {
                    buffer.append(separator);
                }
                if (clouser != '\0') {
                    buffer.append(clouser)
                        .append(value)
                        .append(clouser);
                } else {
                    buffer.append(value);
                }
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#end(java.lang.Class)
         */
        @Override
        public void end(Class<?> clazz) {
            if (level == 1 || level == 2) {
                if (level == 2 || (buffer.length() > 0 && maxLevel < 2)) {
                    buffer.append(newline);
                }
                if (values != null) {
                    String c = String.valueOf(clouser);
                    Collector<CharSequence, ?, String> collector = clouser != '\0' ? Collectors.joining(c + separator + c, c, c)
                            : Collectors.joining(String.valueOf(separator));
                    buffer.append(values.stream()
                        .collect(collector))
                        .append(newline);
                    values = null;
                }
                if (out != null && buffer.length() > bufferSize) {
                    flush();
                    buffer.setLength(0);
                }
            } else if (level > 2) {
                String line = nested.stream()
                    .collect(Collectors.joining(innerSeparator));
                if (values != null) {
                    values.add(line);
                } else {
                    if (!firstColumn) {
                        buffer.append(separator);
                    }
                    if (clouser != '\0') {
                        buffer.append(clouser)
                            .append(line)
                            .append(clouser);
                    } else {
                        buffer.append(line);
                    }
                }
                nested.clear();
                firstColumn = false;
            }
            level--;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.function.Supplier#get()
         */
        @Override
        public String get() {
            if (out != null) {
                flush();
                return null;
            }
            return buffer.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Tool.Traverser#isCompact()
         */
        @Override
        public boolean isCompact() {
            return false;
        }
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
    public static void printReplace(PrintWriter writer, String text, TryTriConsumer<PrintWriter, String, String> replacer, String... closures) {
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
                    String tag = text.substring(begin + prefix.length(), end)
                        .trim();
                    Try.triC(replacer)
                        .accept(writer, tag, prefix);
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
     * @param text Target text
     * @param regex Regex(match pattern)
     * @param replacer MatchResult to String
     * @return Replaced text
     */
    public static String replaceAll(CharSequence text, String regex, Function<MatchResult, String> replacer) {
        Matcher matcher = Pattern.compile(regex)
            .matcher(text);
        if (!matcher.find()) {
            return text.toString();
        }
        StringBuilder s = new StringBuilder();
        int index = 0;
        do {
            s.append(text.subSequence(index, matcher.start()))
                .append(replacer.apply(matcher.toMatchResult()));
            index = matcher.end();
        } while (matcher.find());
        return s.append(text.subSequence(index, text.length()))
            .toString();
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
     * pad("12", "0000") = 0012 pad("12", "0000", true) = 1200
     * 
     * @param value Value
     * @param pad Padding
     * @param right Right join if true
     * @return Padding value
     */
    public static String pad(Object value, String pad, boolean... right) {
        if (right.length > 0 && right[0]) {
            return (value + pad).substring(0, pad.length());
        }
        String text = pad + value;
        return text.substring(Math.max(0, text.length() - pad.length()));
    }

    
    /**
     * MIME type map
     */
    public static Map<String, String> mimeTypeMap;
    static {
    	mimeTypeMap = new HashMap<>();
    	toURL("META-INF/mime.types").ifPresent(url -> {
    		try(BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
    			reader.lines().forEach(line -> {
    				final String[] items = line.split("\\s");
    				final String type = items[0];
    				Stream.of(items).skip(1).map(String::trim).filter(i -> !i.isEmpty() && !i.startsWith("#")).map(String::toLowerCase).forEach(extension -> mimeTypeMap.put(extension, type));
    			});
    		} catch (IOException e) {
                Logger.getGlobal().warning("cannot read: " + e);
			}
    	});
    }
    
    /**
     * MIME type from file extension(using META-INF/mime.types)
     *
     * @param file file name with extension
     * @return MIME type
     */
    public static String getContentType(String file) {
    	if(file == null) {
    		return null;
    	}
        return mimeTypeMap.getOrDefault(trim(".", getExtension(file).toLowerCase(), null), "application/octet-stream");
    }

    /**
     * @param file file
     * @return true if text contents file
     */
    public static boolean isTextContent(String file) {
        String lower = file.toLowerCase(Locale.ENGLISH);
        return Sys.text_extensions.stream()
            .anyMatch(lower::endsWith);
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
    public static <T> Optional<T> at(T[] array, int i) {
        int max = array.length;
        int n = i < 0 ? max + i : i;
        return n < 0 || max <= n ? Optional.empty() : Optional.ofNullable(array[n]);
    }

    /**
     * list classes from package
     *
     * @param packageName package name
     * @return class stream(must to close)
     */
    public static Stream<Class<?>> getClasses(String packageName) {
        String prefix = string(packageName)
            .map(i -> i + '.')
            .orElse("");
        return getResources(packageName.replace('.', '/')).filter(s -> s.endsWith(".class"))
            .map(f -> prefix + f.substring(0, f.length() - ".class".length()))
            .<Class<?>>map(Try.f(Class::forName));
    }

    /**
     * list files
     *
     * @param location location
     * @return file name stream(must to close)
     */
    public static Stream<String> getResources(String location) {
        if (!string(location)
            .isPresent()) {
            Collector<URL, ?, Map<Boolean, List<URL>>> collector = Collectors.partitioningBy(url -> url.toString()
                .contains(".jar!"));
            return toURLs("app")
                .collect(collector)
                .entrySet()
                .stream()
                .flatMap(e -> e.getValue()
                    .stream()
                    .flatMap(url -> (e.getKey() ? getResourcesFromJar(location, Try.s(() -> ((JarURLConnection) url.openConnection()).getJarFile())
                        .get())
                            : getResourcesFromFolder(new File(Try.s(url::toURI)
                                .get()).getParentFile()))));
        }
        return toURLs(location)
            .flatMap(Try.f(i -> {
                boolean isDirectory = Try.<URL>p(j -> new File(Try.s(j::toURI)
                    .get()).isDirectory(), (e, j) -> false)
                    .test(i);
                if ("jar".equals(i.getProtocol())) {
                    return getResourcesFromJar(location, ((JarURLConnection) i.openConnection()).getJarFile());
                } else if (isDirectory) {
                    return getResourcesFromFolder(new File(Try.s(i::toURI)
                        .get()));
                } else {
                    return getResourcesFromJar("", new JarFile(Try.s(i::toURI)
                        .get()
                        .getPath()));
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
        String l = trim("/", location, null);
        return jar.stream()
            .map(JarEntry::getName)
            .filter(i -> i.startsWith(l))
            .map(i -> trim("/", i.substring(l.length()), null).replace('/', '.'));
    }

    /**
     * utf8 decoder
     */
    static final CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .onMalformedInput(CodingErrorAction.REPLACE);

    /**
     * list files in folder
     *
     * @param folder folder
     * @return file name stream(must to close)
     */
    private static Stream<String> getResourcesFromFolder(File folder) {
        try {
        	Path base = folder.toPath();
            return Files.walk(base)
                .map(p -> base.relativize(p).toString().replace(File.separatorChar, '.'));
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
     * InputStream to bytes
     *
     * @param in input
     * @return bytes
     */
    public static byte[] loadBytes(InputStream in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            for (;;) {
                int n;
                n = in.read(buffer);
                if (n < 0) {
                    break;
                }
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            Try.r(in::close)
                .run();
        }
        return out.toByteArray();
    }

    /**
     * @param in input
     * @return lines
     */
    public static Stream<String> lines(InputStream in) {
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

        }, false)
            .onClose(Try.r(reader::close));
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
        if (key != null) {
            map.put(key, value);
        }
        if (keyValues != null) {
            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                map.put((K) keyValues[i], (V) keyValues[i + 1]);
            }
        }
        return map;
    }

    /**
     * @param <K> key type
     * @param <V> value type
     * @return map
     */
    public static <K, V> Map<K, V> map() {
        return map(null, null);
    }

    /**
     * @param <T> Value type
     * @param values Values
     * @return Set
     */
    @SafeVarargs
    public static <T> Set<T> set(T... values) {
        return values == null ? new HashSet<>() : new HashSet<>(Arrays.asList(values));
    }

    /**
     * @param <T> Value type
     * @param values Values
     * @return List
     */
    @SafeVarargs
    public static <T> List<T> list(T... values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(values));
    }

    /**
     * @param text Text
     * @param separator Separator
     * @param pairSeparator Pair separator
     * @return Map
     */
    public static Map<String, String> parseMap(String text, String separator, String pairSeparator) {
        return string(text).map(s -> Stream.of(s.split(separator))
            .collect(() -> (Map<String, String>) new LinkedHashMap<String, String>(), (map,
                    ss) -> peek(s.split(pairSeparator, 2), a -> map.put(a[0], a[1])), Map::putAll))
            .orElseGet(Collections::emptyMap);
    }

    /**
     * @param texts Text array
     * @param pairSeparator Pair separator
     * @return Map
     */
    public static Map<String, String> parseMap(String[] texts, String pairSeparator) {
        return Stream.of(texts)
            .collect(() -> new LinkedHashMap<String, String>(), (map, s) -> peek(s.split(pairSeparator, 2), a -> map.put(a[0], a[1])), Map::putAll);
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
    public static long nextMillis(String text, final ZonedDateTime from) {
        Objects.requireNonNull(from);
        if (Objects.requireNonNull(text)
            .length() <= 0) {
            return -1;
        }
        //eval
        if(text.indexOf('{') >= 0) {
            text = Formatter.format(text, Formatter::excludeForScript, Tool::scriptEscape, Locale.getDefault(), null);
        }
        if(text == null || text.length() <= 0) {
            return -1;
        }
        String value = text.trim()
            .toUpperCase(Locale.ENGLISH);
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
                next = zip(fields.stream(), Stream.of(value.substring(timeIndex)
                    .trim()
                    .split("[^0-9]"))
                    .map(s -> string(s)
                        .map(Long::valueOf)
                        .orElse(-1L)))
                    .peek(i -> {
                        if (i.r < 0) {
                            field[0] = i.l;
                        } else {
                            field[1] = i.l;
                        }
                    })
                    .reduce(next, (i, pair) -> pair.r < 0 ? i : i.with(pair.l, pair.r), (i, j) -> i)
                    .truncatedTo(units[fields.indexOf(field[1]) + 1]);
                if (from.isAfter(next)) {
                    next = next.plus(1, units[fields.indexOf(field[0]) + 1]);
                }
                value = value.substring(0, timeIndex)
                    .trim();
            } else {
                next = next.truncatedTo(ChronoUnit.DAYS);
            }
            if (!value.isEmpty()) {
                final ZonedDateTime start = next;
                boolean until = true;
                for (DayOfWeek i : DayOfWeek.values()) {
                    if (i.name()
                        .startsWith(value)) {
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
                    next = zip(fields.stream(), values.stream()
                        .map(Long::valueOf))
                        .peek(i -> field[0] = i.l)
                        .reduce(next, (i, pair) -> i.with(pair.l, pair.r), (i, j) -> i);
                    if (from.isAfter(next)) {
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
        MessageDigest digest = Try.s(() -> MessageDigest.getInstance(algorithm))
            .get();
        digest.update(bytes);
        return digest.digest();
    }

    /**
     * @param text text
     * @param algorithm algorithm
     * @return hash
     */
    public static String hash(String text, String algorithm) {
        return hex(digest(text.getBytes(StandardCharsets.UTF_8), algorithm));
    }
 
    /**
     * Hexadecimal letters
     */
    public static final char[] hexLetters = "0123456789ABCDEF".toCharArray();
    
    /**
     * bytes to hex string
     * @param bytes bytes
     * @return hex string
     */
    public static String hex(final byte[] bytes) {
    	final int l = bytes.length;
    	final char[] out = new char[l + l];
   	    int j = 0;
   	    for (final byte b : bytes) {
    	    out[j++] = hexLetters[(b >>> 4) & 0x0F];
    	    out[j++] = hexLetters[b & 0x0F];
    	}
    	return new String(out);
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
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digest(key
                .getBytes(StandardCharsets.UTF_8), "MD5"), "AES"), new IvParameterSpec((iv.length > 0 ? iv[0] : Sys.IV).getBytes(StandardCharsets.UTF_8)));
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
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(digest(key
                .getBytes(StandardCharsets.UTF_8), "MD5"), "AES"), new IvParameterSpec((iv.length > 0 ? iv[0] : Sys.IV).getBytes(StandardCharsets.UTF_8)));
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
        return Base64.getUrlEncoder()
            .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param text Base64 encoded text
     * @return text
     */
    public static String base64Decode(String text) {
        try {
            return new String(Base64.getUrlDecoder()
                .decode(text), StandardCharsets.UTF_8);
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
                            throw new IllegalArgumentException("Invalid base128 character: " + new String(buffer, i, i + 1, "MS932")
                                    + String.format(" (0x%02X)", buffer[i]));
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
     * @param <T> Object type
     * @param <R> Result type
     * @param function ObjIntFunction
     * @return Function
     */
    public static <T, R> Function<T, R> withIndexF(ObjIntFunction<T, R> function) {
        AtomicInteger n = new AtomicInteger(-1);
        return obj -> function.apply(obj, n.incrementAndGet());
    }

    /**
     * @param <R> Result type
     * @param function BiIntFunction
     * @return IntFunction
     */
    public static <R> IntFunction<R> withIndexIntF(BiIntFunction<R> function) {
        AtomicInteger n = new AtomicInteger(-1);
        return obj -> function.apply(obj, n.incrementAndGet());
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
     * @param <T> Name type
     * @param <U> Value type
     * @param <V> Container type
     * @param map Map
     * @param name Name
     * @param value Value
     * @param supplier Container supplier
     * @return Value
     */
    public static <T, U, V extends Collection<U>> U addValue(Map<T, V> map, T name, U value, Supplier<V> supplier) {
        V list = map.get(name);
        if (list == null) {
            list = supplier.get();
            map.put(name, list);
        }
        list.add(value);
        return value;
    }

    /**
     * @param <T> Name type
     * @param <U> Value type
     * @param <V> Container type
     * @param map Map
     * @param name Name
     * @param value Value
     * @param supplier Container supplier
     * @return Value
     */
    public static <T, U, V extends Collection<U>> U addValueIfAbsent(Map<T, V> map, T name, U value, Supplier<V> supplier) {
        V list = map.get(name);
        if (list == null) {
            list = supplier.get();
            map.put(name, list);
        }
        if(!list.contains(value)) {
            list.add(value);
        }
        return value;
    }

    /**
     * @param <T> Name type
     * @param <U> Value type
     * @param <V> Container type
     * @param map Map
     * @param name Name
     * @param value Value
     * @param supplier Container supplier
     * @return Value
     */
    public static <T, U, V extends Collection<U>> U setValue(Map<T, V> map, T name, U value, Supplier<V> supplier) {
        V list = supplier.get();
        list.add(value);
        map.put(name, list);
        return value;
    }

    /**
     * @param <T> Name type
     * @param <U> Value type
     * @param <V> Container type
     * @param map Map
     * @param name Name
     * @return First value
     */
    public static <T, U, V extends Collection<U>> Optional<U> getFirst(Map<T, V> map, T name) {
        return of(map)
            .map(m -> m.get(name))
            .filter(a -> !a.isEmpty())
            .map(a -> a.iterator().next());
    }

    /**
     * @param <T> Name type
     * @param <U> Value type
     * @param <V> Container type
     * @param map Map
     * @param name Name
     * @param separator Value separator
     * @return joined value
     */
 	public static <T, U, V extends Collection<U>> Optional<String> getJoin(Map<T, V> map, String name, String separator) {
		return of(map.get(name)).map(i -> i.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(separator))).filter(notEmpty);
	}

    /**
     * @param text Text
     * @param max Max length
     * @param suffix Suffix
     * @return Cut text
     */
    public static String cut(String text, int max, String suffix) {
        return of(text)
            .map(i -> {
                boolean isSuffix = false;
                int index = i.indexOf('\r');
                if (index < 0) {
                    index = i.indexOf('\n');
                } else {
                    int index2 = i.indexOf('\n');
                    if (index2 > 0 && index2 < index) {
                        index = index2;
                    }
                }
                if (index >= 0) {
                    i = i.substring(0, index);
                    isSuffix = true;
                }
                int m = max - suffix.length();
                if (i.length() > m) {
                    i = i.substring(0, m);
                    isSuffix = true;
                }
                return i + (isSuffix ? suffix : "");
            })
            .orElse(null);
    }

    /**
     * Build path
     * 
     * @param first first part
     * @param parts Parts of path
     * @return Separator to Path function
     */
    public static Function<String, String> path(String first, String... parts) {
        return separator -> {
            StringBuilder s = new StringBuilder();
            String[] normalized = Stream.concat(Stream.of(first), Stream.of(parts))
                .filter(part -> part != null && !part.isEmpty())
                .toArray(String[]::new);
            int end = normalized.length - 1;
            if (end > 0) {
                s.append(trim(null, normalized[0], separator));
            }
            for (int i = 1; i < end; i++) {
                s.append(trim(null, prefix(normalized[i], separator), separator));
            }
            if (end == 0) {
                s.append(normalized[end]);
            } else if (end > 0) {
                s.append(prefix(normalized[end], separator));
            }
            return s.toString();
        };
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
        }
        return path.substring(0, end + 1);
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
        }
        int end = path.lastIndexOf('.');
        if (end < 0 || start > end) {
            end = path.length();
        }
        return path.substring(start + 1, end);
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
     * HTML escape(&amp;, &quot;, &lt;, &gt;, &#39;)
     *
     * @param text target
     * @return escaped text
     */
    public static String htmlEscape(Object text) {
        if (text == null) {
            return null;
        }
        String string = text.toString();
        if (string == null || string.isEmpty()) {
            return null;
        }
        StringBuilder s = new StringBuilder();
        string.chars()
            .forEach(c -> {
                switch (c) {
                case '&':
                    s.append("&amp;");
                    break;
                case '"':
                    s.append("&quot;");
                    break;
                case '<':
                    s.append("&lt;");
                    break;
                case '>':
                    s.append("&gt;");
                    break;
                case '\'':
                    s.append("&#39;");
                    break;
                default:
                    s.append((char) c);
                    break;
                }
            });
        return s.toString();
    }

    /**
     * String escape(", \r, \n, \t)
     *
     * @param text target
     * @return escaped text
     */
    public static String scriptEscape(Object text) {
        if (text == null) {
            return null;
        }
        StringBuilder s = new StringBuilder();
        text.toString()
            .chars()
            .forEach(c -> {
                switch (c) {
                case '\\':
                    s.append("\\\\");
                    break;
                case '"':
                    s.append("\\\"");
                    break;
                case '\r':
                    s.append("\\r");
                    break;
                case '\n':
                    s.append("\\n");
                    break;
                case '\t':
                    s.append("\\t");
                    break;
                default:
                    s.append((char) c);
                    break;
                }
            });
        return s.toString();
    }

    /**
     * @param text text
     * @return Character array
     */
    public static Character[] toCharacterArray(String text) {
        return text.chars()
            .mapToObj(j -> Character.valueOf((char) j))
            .toArray(Character[]::new);
    }

    /**
     * DateTimeFormatter toString cache
     */
    public static final Map<String, String> formatCache = new ConcurrentHashMap<>();

    /**
     * UTF-8 byte order mark
     */
    @SuppressFBWarnings("MS_PKGPROTECT")
    public static final byte[] BOM = { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };

    /**
     * year4, month2, day2, hour2, minute2, second2, millisecond3
     */
    public static final DateTimeFormatter uuuuMMddHHmmssSSS = new DateTimeFormatterBuilder().appendPattern("uuuuMMddHHmmss")
        .appendValue(ChronoField.MILLI_OF_SECOND, 3)
        .toFormatter();//for java8 bug

    /**
     * @param pattern Pattern
     * @param locale Locale
     * @return DateTimeFormatter
     */
    public static DateTimeFormatter getFormat(String pattern, Locale locale) {
        return peek(DateTimeFormatter.ofPattern(pattern, locale), format -> formatCache.computeIfAbsent(format.toString(), key -> pattern));
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
            users.put(type, Stream.of(addresses)
                .map(Try.f(InternetAddress::new))
                .toArray(InternetAddress[]::new));
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
            return IntStream.range(0, addressNames.length / 2)
                .map(i -> i * 2)
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
            this.from = Try.s(() -> new InternetAddress(from, name, Sys.Mail.charset))
                .get();
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
        InternetAddress from = of(mail.from)
            .orElseGet(Try.s(() -> new InternetAddress(Sys.Mail.user)));
        try {
            message.setFrom(from);
            message.setReplyTo(mail.replyTo == null ? array(from) : mail.replyTo);
            mail.users.forEach(Try.biC((type, users) -> message.setRecipients(type, users)));
            message.setSubject(subject, Sys.Mail.charset);
            message.setText(body, Sys.Mail.charset);
            message.setHeader("Content-Transfer-Encoding", Sys.Mail.encoding);
            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param <T> Resource type
     * @param <U> Return type
     * @param supplier Resource supplier
     * @param function Resource function
     * @return Value
     */
    public static <T extends AutoCloseable, U> U using(TrySupplier<T> supplier, TryFunction<T, U> function) {
        try (T resource = supplier.get()) {
            return function.apply(resource);
        } catch (Exception e) {
            Logger.getGlobal().warning("resource error: " + e);
            return null;
        }
    }

    /**
     * @param object Object
     * @param outs Output(auto create if empty)
     * @return Serialized value
     */
    public static byte[] serialize(Serializable object, ByteArrayOutputStream... outs) {
        ByteArrayOutputStream out = outs.length > 0 ? outs[0] : new ByteArrayOutputStream();
        try (ObjectOutputStream o = new ObjectOutputStream(out)) {
            o.writeObject(object);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] value = out.toByteArray();
        out.reset();
        return value;
    }

    /**
     * @param bytes Bytes
     * @return object
     */
    public static Serializable deserialize(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes);
             ObjectInputStream i = new ObjectInputStream(in)) {
            return (Serializable) i.readObject();
        } catch (Exception e) {
            Try.catcher.accept(e);
            return null;
        }
    }

    /**
     * @param <L> Left parameter type
     * @param <R> Right parameter type
     * @param <V> Return type
     * @param biFunction Function
     * @param left Left value
     * @return Function
     */
    public static <L, R, V> Function<R, V> bindLeft(BiFunction<L, R, V> biFunction, L left) {
        return right -> biFunction.apply(left, right);
    }

    /**
     * @param <L> Left parameter type
     * @param <R> Right parameter type
     * @param <V> Return type
     * @param biFunction Function
     * @param right Right value
     * @return Function
     */
    public static <L, R, V> Function<L, V> bindRight(BiFunction<L, R, V> biFunction, R right) {
        return left -> biFunction.apply(left, right);
    }

    /**
     * Get class full name without package name
     * 
     * @param clazz Target class
     * @return Full name
     */
    public static String fullName(Class<?> clazz) {
        return trim(".", clazz.getCanonicalName()
            .substring(clazz.getPackage()
                .getName()
                .length()), null);
    }

    /**
     * Create option tags
     * 
     * @param value Initial value
     * @param items Items(value: display)
     * @return Option tags
     */
    public static String options(Object value, Object items) {
        StringBuilder s = new StringBuilder();
        String valueText = String.valueOf(value);
        Iterator<?> iterator;
        if (items instanceof BaseStream) {
            iterator = ((BaseStream<?, ?>) items).iterator();
        } else if (items instanceof Iterable) {
            iterator = ((Iterable<?>) items).iterator();
        } else {
            iterator = Reflector.invoke(items, "iterator", array());
        }
        iterator
            .forEachRemaining(i -> s.append("<option value=\"" + htmlEscape(((Map.Entry<?, ?>) i).getKey()) + (String.valueOf(((Map.Entry<?, ?>) i).getKey())
                .equals(valueText) ? "\" selected=\"selected" : "") + "\">" + htmlEscape(((Map.Entry<?, ?>) i).getValue()) + "</option>"));
        return s.toString();
    }

    /**
     * Create check tags
     * 
     * @param name Name
     * @param value Initial value
     * @param items Items(value: display)
     * @return check tags
     */
    public static String checks(String name, Object value, Object items) {
        StringBuilder s = new StringBuilder();
        String valueText = String.valueOf(value);
        Iterator<?> iterator;
        if (items instanceof BaseStream) {
            iterator = ((BaseStream<?, ?>) items).iterator();
        } else if (items instanceof Iterable) {
            iterator = ((Iterable<?>) items).iterator();
        } else {
            iterator = Reflector.invoke(items, "iterator", array());
        }
        iterator.forEachRemaining(i -> s.append("<label class=\"checkbox\"><input type=\"checkbox\" name=\"" + name + "\" value=\""
                + htmlEscape(((Map.Entry<?, ?>) i).getKey()) + (String.valueOf(((Map.Entry<?, ?>) i).getKey())
                    .equals(valueText) ? "\" checked=\"checked" : "")
                + "\">" + htmlEscape(((Map.Entry<?, ?>) i).getValue()) + "</label>"));
        return s.toString();
    }

    /**
     * Create radio tags
     * 
     * @param name Name
     * @param value Initial value
     * @param items Items(value: display)
     * @return radio tags
     */
    public static String radios(String name, Object value, Object items) {
        StringBuilder s = new StringBuilder();
        String valueText = String.valueOf(value);
        Iterator<?> iterator;
        if (items instanceof BaseStream) {
            iterator = ((BaseStream<?, ?>) items).iterator();
        } else if (items instanceof Iterable) {
            iterator = ((Iterable<?>) items).iterator();
        } else {
            iterator = Reflector.invoke(items, "iterator", array());
        }
        iterator.forEachRemaining(i -> s.append("<label class=\"radio\"><input type=\"radio\" name=\"" + name + "\" value=\""
                + htmlEscape(((Map.Entry<?, ?>) i).getKey()) + (String.valueOf(((Map.Entry<?, ?>) i).getKey())
                    .equals(valueText) ? "\" checked=\"checked" : "")
                + "\">" + htmlEscape(((Map.Entry<?, ?>) i).getValue()) + "</label>"));
        return s.toString();
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
        // String text = "target text!";
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
        // Log.info("base128encoded: " + base128Decode(text));
        // Log.info("base128decoded: " + base128Decode(base128Encode(text)));
        // Log.info(Session.currentLocale().toString());
        // Log.info(camelToSnake("LoginURL"));
        // System.out.println(java.time.format.DateTimeFormatter.ofPattern("Gy/M/d(E)", Locale.JAPAN)
        // .format(java.time.chrono.JapaneseDate.now()));
        //enumOf(java.time.Month::getValue, 1).ifPresent(System.out::println);
        //System.out.println(in("id", IntStream.rangeClosed(1, 1001).toArray()));
    	System.out.println(hex(new byte[] {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17}));
    	System.out.println(getContentType("a.txt"));
    	System.out.println(getContentType(".txt"));
    	System.out.println(getContentType(".Txt"));
    	System.out.println(getContentType(""));
    	System.out.println(getContentType(".n3"));
    	System.out.println(getContentType(null));
    }

    /**
     * @param <T> Value type
     * @param map Map
     * @param key Key
     * @return Value
     */
    public static <T> Optional<T> getIgnoreCase(Map<String, T> map, String key) {
        return map.entrySet()
            .stream()
            .filter(e -> e.getKey()
                .equalsIgnoreCase(key))
            .findFirst()
            .map(Map.Entry::getValue);
    }

    /**
     * @param size Character count
     * @return Current time
     */
    public static String now(int size) {
        return uuuuMMddHHmmssSSS.format(LocalDateTime.now())
            .substring(0, size);
    }

    /**
     * @return Current nendo
     */
    public static int nendo() {
        LocalDate d = LocalDate.now();
        return d.getMonthValue() < Sys.nendo_start_month ? d.getYear() - 1 : d.getYear();
    }

    /**
     * @param year Year
     * @param month Month
     * @return Nendo
     */
    public static int nendo(int year, int month) {
        return month < Sys.nendo_start_month ? year - 1 : year;
    }

    /**
     * @param <T> Enum type
     * @param <U> Comparing type
     * @param get Comparing value getter
     * @param value Value
     * @param clazz Enum class
     * @return Enum value
     */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, U> Optional<T> enumOf(Function<T, U> get, U value, T... clazz) {
        return Stream.of((T[]) clazz.getClass()
            .getComponentType()
            .getEnumConstants())
            .filter(i -> get.apply(i)
                .equals(value))
            .findFirst();
    }

    /**
     * Build attribute(name="value")
     * 
     * @param name Attribute name
     * @param value Attribute value
     * @return Attribute text
     */
    public static String attr(String name, String value) {
        return ' ' + name + "=\"" + value + '"';
    }

    /**
     * Build attribute(name="name")
     * 
     * @param name Attribute name
     * @return Attribute text
     */
    public static String attr(String name) {
        return attr(name, name);
    }

    /**
     * IN句を作成(中身を1000単位で分ける)
     * @param field 列名
     * @param ids 値
     * @return IN句
     */
    public static String in(String field, int... ids) {
        if(ids == null || ids.length <= 0) {
            return field + " <> " + field;
        }
        List<String> items = list();
        for(int i = 0; i < ids.length; i += 1000) {
            items.add(field + " IN(" + IntStream.of(ids).skip(i).limit(1000)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(", ")) + ")");
        }
        return items.size() == 1 ? items.get(0) : "(" + String.join(" OR ", items) + ")";
    }

    /**
     * IN句を作成(中身を1000単位で分ける)
     * @param field 列名
     * @param ids 値
     * @return IN句
     */
    public static String in(String field, String... ids) {
        if(ids == null || ids.length <= 0) {
            return field + " <> " + field;
        }
        List<String> items = list();
        for(int i = 0; i < ids.length; i += 1000) {
            items.add(field + " IN(" + Stream.of(ids).skip(i).limit(1000)
            .collect(Collectors.joining(", ")) + ")");
        }
        return items.size() == 1 ? items.get(0) : "(" + String.join(" OR ", items) + ")";
    }

    /**
     * @param <T> 要素の型
     * @param text HTMLエスケープしてから改行を&lt;br/&gt;に変換
     * @return 変換結果
     */
    @SafeVarargs
	public static <T extends Object> String brs(T... text) {
        String s = htmlEscape(Stream.of(text).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining("\n")));
        return s == null ? null : s.replaceAll("\r?\n", "<br/>");
    }

    /**
     * el用
     * 
     * @param text HTMLエスケープしてから改行を&lt;br/&gt;に変換
     * @return 変換結果
     */
    public static String br(Object text) {
        return brs(text);
    }
}
