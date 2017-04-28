package framework;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Binder
 */
public class Binder {
    /**
     * parameters
     */
    Map<String, List<String>> parameters;

    /**
     * @param parameters parameters
     */
    public Binder(Map<String, List<String>> parameters) {
        this.parameters = parameters;
    }

    /**
     * @param parameters parameters
     * @return map
     */
    public static Map<String, Object> toMap(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Tool.map();
        }
        throw new UnsupportedOperationException();
    }

    /**
     * @param clazz class
     * @param text text
     * @param error action if error(allow null:retry that text is "0")
     * @return value
     */
    Object convert(String text, Type clazz, Function<Exception, Object> error) {
        Function<Function<String, Object>, Object> toNumber = f -> Try
                .s(() -> f.apply(text), error == null ? (Function<Exception, Object>) (e -> f.apply("0")) : error).get();
        if (clazz == String.class) {
            return text == null ? error == null ? "" : error.apply(null) : text;
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return toNumber.apply(Byte::valueOf);
        }
        if (clazz == short.class || clazz == Short.class) {
            return toNumber.apply(Short::valueOf);
        }
        if (clazz == int.class || clazz == Integer.class) {
            return toNumber.apply(Integer::valueOf);
        }
        if (clazz == long.class || clazz == Long.class) {
            return toNumber.apply(Long::valueOf);
        }
        if (clazz == char.class || clazz == Character.class) {
            return text != null && text.length() > 0 ? text.charAt(0) : error == null ? '\0' : error.apply(null);
        }
        if (clazz == float.class || clazz == Float.class) {
            return toNumber.apply(Float::valueOf);
        }
        if (clazz == double.class || clazz == Double.class) {
            return toNumber.apply(Double::valueOf);
        }
        return text;
    }

    /**
     * @param name name
     * @param clazz class
     * @param parameterizedType Parameterized type
     * @return value
     */
    public Object bind(String name, Class<?> clazz, Type... parameterizedType) {
        if (clazz == null) {
            return null;
        }
        List<String> values = parameters.get(name);
        String first = values == null || values.isEmpty() ? null : values.get(0);

        // Array
        Class<?> component = clazz.getComponentType();
        if (component != null) {
            return values.stream().map(value -> convert(value, component, null)).toArray(n -> (Object[]) Array.newInstance(component, n));
        }

        // Optional
        if (clazz == Optional.class) {
            Object value = convert(first, parameterizedType[0], e -> null);
            return Tool.of(value);
        }

        return convert(first, clazz, null);
    }
}
