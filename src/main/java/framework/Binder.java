package framework;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
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
     * @param parameters
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
            return Tool.jsonMap();
        }
        throw new UnsupportedOperationException();
    }
    
    /**
     * @param clazz class
     * @param text text
     * @return value
     */
    Object conv(Class<?> clazz, String text) {
        Function<Function<String, Object>, Object> convert = f -> Try.s(() -> f.apply(text), e -> f.apply("0")).get();
        if (clazz == String.class) {
            return text;
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return convert.apply(Byte::valueOf);
        }
        if (clazz == short.class || clazz == Short.class) {
            return convert.apply(Short::valueOf);
        }
        if (clazz == int.class || clazz == Integer.class) {
            return convert.apply(Integer::valueOf);
        }
        if (clazz == long.class || clazz == Long.class) {
            return convert.apply(Long::valueOf);
        }
        if (clazz == char.class || clazz == Character.class) {
            return text.length() > 0 ? text.charAt(0) : '\0';
        }
        if (clazz == float.class || clazz == Float.class) {
            return convert.apply(Float::valueOf);
        }
        if (clazz == double.class || clazz == Double.class) {
            return convert.apply(Double::valueOf);
        }
        return text;
    }

    /**
     * @param name name
     * @param clazz class
     * @return value
     */
    public Object bind(String name, Class<?> clazz) {
        if(clazz == null) {
            return null;
        }
        List<String> values = parameters.get(name);
        String nonNull = values == null ? "" : values.get(0);
        Class<?> component = clazz.getComponentType();
        if(component != null) {
            return values.stream().map(value -> conv(component, value)).toArray(n -> (Object[])Array.newInstance(component, n));
        }
        return conv(clazz, nonNull);
    }
}
