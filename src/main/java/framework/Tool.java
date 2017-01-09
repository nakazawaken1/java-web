package framework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.activation.MimetypesFileTypeMap;

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

    public static void printFormat(PrintWriter writer, String line, TryBiConsumer<PrintWriter, String> replacer, String... closures) {
        for (boolean loop = true; loop;) {
            loop = false;
            for (int i = 0; i < closures.length; i += 2) {
                String prefix = closures[i];
                String suffix = closures[i + 1];
                int begin = line.indexOf(prefix);
                if (begin < 0) {
                    continue;
                }
                int end = line.indexOf(suffix, begin);
                if (end < 0) {
                    continue;
                }
                writer.print(line.substring(0, begin));
                if (replacer != null) {
                    String tag = line.substring(begin + prefix.length(), end);
                    Try.c(replacer).accept(writer, tag);
                }
                line = line.substring(end + suffix.length());
                loop = true;
            }
        }
        if (!line.isEmpty()) {
            writer.print(line);
        }
    }

    /**
     * @param left left trimming characters
     * @param text target text
     * @param right right trimming characters
     * @return trimed text
     */
    public static String trim(String left, String text, String right) {
        if(text == null) {
            return null;
        }
        int start = 0;
        int end = text.length() - 1;
        while(start <= end) {
            if(left.indexOf(text.charAt(start)) < 0) {
                break;
            }
            start++;
        }
        while(start <= end) {
            if(right.indexOf(text.charAt(end)) < 0) {
                break;
            }
            end--;
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
}
