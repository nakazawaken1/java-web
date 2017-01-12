<<<<<<< HEAD
package framework;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

import framework.Try.TryBiConsumer;

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
     * @param replacer replacer
     * @param closures closures
     */
    public static void printFormat(PrintWriter writer, String text, TryBiConsumer<PrintWriter, String> replacer, String... closures) {
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
                    String tag = text.substring(begin + prefix.length(), end);
                    Try.c(replacer).accept(writer, tag);
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
     * @param key key
     * @param value value
     * @return entry
     */
    public static <K, V> Map.Entry<K, V> pair(K key, V value) {
        return new AbstractMap.SimpleEntry<K, V>(key, value);
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
}
=======
package framework;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

import framework.Try.TryBiConsumer;

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
     * @param replacer replacer
     * @param closures closures
     */
    public static void printFormat(PrintWriter writer, String text, TryBiConsumer<PrintWriter, String> replacer, String... closures) {
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
                    String tag = text.substring(begin + prefix.length(), end);
                    Try.c(replacer).accept(writer, tag);
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
     * @param key key
     * @param value value
     * @return entry
     */
    public static <K, V> Map.Entry<K, V> pair(K key, V value) {
        return new AbstractMap.SimpleEntry<K, V>(key, value);
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
}
>>>>>>> branch 'master' of https://github.com/nakazawaken1/java-web.git
