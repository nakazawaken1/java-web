package framework;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * reflection object cache
 */
public class Reflector {
    /**
     * constructor cache{class, arguments hash : constructor}
     */
    static Map<Tuple<Class<?>, Integer>, Constructor<?>> constructors = new HashMap<>();

    /**
     * constructor cache{class : {field name : field}}
     */
    static Map<Class<?>, Map<String, Field>> fields = new HashMap<>();

    /**
     * constructor cache{class : instance}
     */
    static Map<Class<?>, Object> instances = new HashMap<>();

    /**
     * @param <T> target type
     * @param clazz target class
     * @param args constructor arguments
     * @return constructor
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<Constructor<T>> constructor(Class<T> clazz, Class<?>... args) {
        try {
            return Optional.ofNullable((Constructor<T>) constructors.computeIfAbsent(Tuple.of(clazz, Arrays.hashCode(args)), Try.f(pair -> {
                Constructor<?> result = pair.l.getDeclaredConstructor(args);
                result.setAccessible(true);
                return result;
            })));
        } catch (RuntimeException e) {
            Tool.getLogger().log(Level.INFO, "constructor error", e);
            return Optional.empty();
        }
    }

    /**
     * call no args constructor with cache
     * 
     * @param <T> target type
     * @param clazz target class
     * @return instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T constInstance(Class<T> clazz) {
        return (T) instances.computeIfAbsent(clazz, Reflector::instance);
    }

    /**
     * call no args constructor
     * 
     * @param <T> target type
     * @param clazz target class
     * @return instance
     */
    public static <T> T instance(Class<T> clazz) {
        return constructor(clazz).map(Try.f(Constructor::newInstance)).orElse(null);
    }

    /**
     * @param <T> target type
     * @param clazz class
     * @return Class<T>
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<Class<T>> clazz(String clazz) {
        try {
            return Optional.ofNullable((Class<T>) Class.forName(clazz));
        } catch (ClassNotFoundException e) {
            Tool.getLogger().log(Level.INFO, "class error", e);
            return Optional.empty();
        }
    }

    /**
     * @param clazz class
     * @param name field name
     * @return field
     */
    public static Optional<Field> field(Class<?> clazz, String name) {
        return Optional
                .ofNullable(fields
                        .computeIfAbsent(clazz,
                                c -> Stream.of(c.getDeclaredFields()).peek(f -> f.setAccessible(true)).collect(Collectors.toMap(Field::getName, f -> f)))
                        .get(name));
    }

    /**
     * @param clazz class
     * @return Stream of field name and instance
     */
    public static Stream<Map.Entry<String, Field>> fields(Class<?> clazz) {
        return Stream.of(fields.computeIfAbsent(clazz,
                c -> Stream.of(c.getDeclaredFields()).peek(f -> f.setAccessible(true)).collect(Collectors.toMap(Field::getName, f -> f))));
    }
}
