package framework;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import framework.annotation.Mapping;

/**
 * reflection object cache
 */
public class Reflector {
    /**
     * constructor cache{class, arguments hash : constructor}
     */
    static Map<Tuple<Class<?>, Integer>, Constructor<?>> constructors = new ConcurrentHashMap<>();

    /**
     * method cache{class, arguments hash : method}
     */
    static Map<Tuple<Class<?>, Integer>, Method> methods = new ConcurrentHashMap<>();

    /**
     * field cache{class : {field name : field}}
     */
    static Map<Class<?>, Map<String, Field>> fields = new ConcurrentHashMap<>();

    /**
     * mapping field cache{class : {mapping field name : field}}
     */
    static Map<Class<?>, Map<String, Field>> mappingFields = new ConcurrentHashMap<>();

    /**
     * instance cache{class : instance}
     */
    static Map<Class<?>, Object> instances = new ConcurrentHashMap<>();

    /**
     * @param <T> target type
     * @param clazz target class
     * @param args constructor arguments
     * @return constructor
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<Constructor<T>> constructor(Class<T> clazz, Class<?>... args) {
        try {
            return Tool.of((Constructor<T>) constructors.computeIfAbsent(Tuple.of(clazz, Arrays.hashCode(args)), Try.f(pair -> {
                Constructor<?> result = pair.l.getDeclaredConstructor(args);
                result.setAccessible(true);
                return result;
            })));
        } catch (RuntimeException e) {
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
     * call no args constructor
     * 
     * @param <T> target type
     * @param clazz target class
     * @return instance
     */
    public static <T> T instance(String clazz) {
        return instance(Reflector.<T>clazz(clazz).orElseThrow(RuntimeException::new));
    }

    /**
     * @param <T> target type
     * @param clazz class
     * @return Class&lt;T&gt;
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<Class<T>> clazz(String clazz) {
        try {
            return Tool.of((Class<T>) Class.forName(clazz));
        } catch (ClassNotFoundException e) {
            Log.info(e, () -> "class error");
            return Optional.empty();
        }
    }

    /**
     * @param clazz class
     * @param name field name
     * @return field
     */
    public static Optional<Field> field(Class<?> clazz, String name) {
        return Tool.of(fields(clazz).get(name));
    }

    /**
     * @param clazz class
     * @return Stream of field name and instance
     */
    public static Map<String, Field> fields(Class<?> clazz) {
        return fields.computeIfAbsent(clazz,
                c -> Stream.of(c.getDeclaredFields()).peek(f -> f.setAccessible(true)).collect(Collectors.toMap(Field::getName, f -> f)));
    }

    /**
     * @param clazz class
     * @param name field name
     * @return field
     */
    public static Optional<Field> mappingField(Class<?> clazz, String name) {
        return Tool.of(mappingFields(clazz).get(name));
    }

    /**
     * @param clazz class
     * @return Stream of field name and instance
     */
    public static Map<String, Field> mappingFields(Class<?> clazz) {
        return mappingFields.computeIfAbsent(clazz,
                c -> Stream.of(c.getDeclaredFields()).peek(f -> f.setAccessible(true)).collect(Collectors.toMap(Reflector::mappingFieldName, f -> f)));
    }

    /**
     * @param field target field
     * @return name
     */
    public static String mappingFieldName(Field field) {
        return Tool.of(field.getAnnotation(Mapping.class)).map(Mapping::value).flatMap(Tool::string).orElseGet(field::getName);
    }

    /**
     * @param clazz instance or class
     * @return table name
     */
    public static String mappingClassName(Object clazz) {
        Class<?> start = clazz instanceof Class ? (Class<?>) clazz : clazz.getClass();
        for (Class<?> c = start; c != Object.class; c = c.getSuperclass()) {
            Mapping name = c.getAnnotation(Mapping.class);
            if (name != null) {
                String value = name.value();
                if (Tool.string(value).isPresent()) {
                    return value;
                }
            }
        }
        return start.getSimpleName().toLowerCase(Locale.ENGLISH);
    }

    /**
     * @param clazz class
     * @param name name
     * @param args argument types
     * @return method
     */
    public static Optional<Method> method(Class<?> clazz, String name, Class<?>... args) {
        return Tool.of(
                methods.computeIfAbsent(Tuple.of(clazz, Objects.hash(name, Arrays.hashCode(args))), Try.f(pair -> pair.l.getMethod(name, args), (e, pair) -> {
                    Method result = Try.s(() -> pair.l.getDeclaredMethod(name, args), ee -> null).get();
                    if (result != null) {
                        result.setAccessible(true);
                    }
                    return result;
                })));
    }

    /**
     * @param <T> Annotation type
     * @param annotationClass annotation class
     * @return field to annotation function
     */
    public static <T extends Annotation> Function<Field, Optional<T>> annotation(Class<T> annotationClass) {
        return field -> Tool.of(field.getAnnotation(annotationClass));
    }

    /**
     * @param annotationClass annotation class
     * @return predicate
     */
    public static Predicate<Field> hasAnnotation(Class<? extends Annotation> annotationClass) {
        return field -> field.getAnnotation(annotationClass) != null;
    }

    /**
     * @param field Field
     * @param index Index
     * @return Generic type
     */
    public static Class<?> getGenericParameter(Field field, int index) {
        return (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[index];
    }

    /**
     * @param <T> Return type
     * @param clazz Class
     * @param index Index
     * @return Generic type
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getGenericParameter(Class<?> clazz, int index) {
        return (Class<T>) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[index];
    }

    /**
     * @param <T> Value type
     * @param annotation Target
     * @param methodName Method name
     * @return Default value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getDefaultValue(Class<? extends Annotation> annotation, String methodName) {
        try {
            return (T) annotation.getMethod(methodName).getDefaultValue();
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }
    }

    /**
     * @param value Value
     * @return True if modified value else false
     */
    public static boolean isModified(Object value) {
        if (value == null || value == Optional.empty()) {
            return false;
        }
        if (value instanceof Boolean) {
            return (boolean) value != false;
        }
        if (value instanceof Byte) {
            return (byte) value != (byte) 0;
        }
        if (value instanceof Short) {
            return (short) value != (short) 0;
        }
        if (value instanceof Integer) {
            return (int) value != (int) 0;
        }
        if (value instanceof Long) {
            return (long) value != (long) 0;
        }
        if (value instanceof Float) {
            return (float) value != (float) 0;
        }
        if (value instanceof Double) {
            return (double) value != (double) 0;
        }
        if (value instanceof Character) {
            return (char) value != (char) 0;
        }
        return true;
    }

    /**
     * @param <T> type
     * @param instance instance
     * @param name Property name
     * @param orElse call if not exists property
     * @return Property value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getProperty(Object instance, String name, Supplier<T> orElse) {
        if (instance != null) {
            Class<?> clazz = instance.getClass();
            Method m = method(clazz, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))
                    .orElseGet(() -> name.startsWith("is") ? method(clazz, name).orElse(null) : null);
            if (m != null) {
                try {
                    return (T) m.invoke(instance);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    Log.warning(e, () -> "get property error");
                }
            }
        }
        return orElse.get();
    }

    /**
     * @param <T> Return type
     * @param methodFullName Full method name
     * @param argClasses Argument classes
     * @param args Arguments
     * @return return Value
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodFullName, Class<?>[] argClasses, Object... args) {
        int i = methodFullName.lastIndexOf('.');
        return i < 0 ? null
                : (T) clazz(methodFullName.substring(0, i)).flatMap(c -> method(c, methodFullName.substring(i + 1), argClasses))
                        .map(Try.f(m -> m.invoke(null, args))).orElse(null);
    }

    /**
     * @param <T> Return type
     * @param instance Instance
     * @param name Method name
     * @param argClasses Argument classes
     * @param args Arguments
     * @return return Value
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object instance, String name, Class<?>[] argClasses, Object... args) {
        return (T) method(instance.getClass(), name, argClasses).map(Try.f(m -> m.invoke(null, args))).orElse(null);
    }
}
