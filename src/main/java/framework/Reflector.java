package framework;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    static Map<Tuple<Class<?>, Function<Field, String>>, Map<String, Field>> fields = new ConcurrentHashMap<>();

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
        return instance(Reflector.<T>clazz(clazz).<RuntimeException>orElseThrow(RuntimeException::new));
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
	    Log.config(e.toString());
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
    	return fields(clazz, Field::getName);
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
     * @param getName Name getter
     * @return Stream of field name and instance
     */
    public static Map<String, Field> fields(Class<?> clazz, Function<Field, String> getName) {
	return fields.computeIfAbsent(Tuple.of(clazz, getName), pair -> {
	    Class<?> c = pair.l;
	    Map<String, Field> map = new LinkedHashMap<>();
	    while (c != Object.class && c != null) {
		Stream.of(c.getDeclaredFields()).map(f -> Tuple.of(getName.apply(f), f))
			.filter(t -> !map.containsKey(t.l)).peek(t -> t.r.setAccessible(true))
			.forEach(f -> map.put(f.l, f.r));
		c = c.getSuperclass();
	    }
	    return map;
	});
    }

    /**
     * @param clazz class
     * @return Stream of field name and instance
     */
    public static Map<String, Field> mappingFields(Class<?> clazz) {
    	return fields(clazz, Reflector::mappingFieldName);
    }

    /**
     * @param field target field
     * @return name
     */
    public static String mappingFieldName(Field field) {
		Mapping fieldAnnotation = field.getAnnotation(Mapping.class);
		Class<?> clazz = field.getDeclaringClass();
		return Tool.or(fieldAnnotation, () -> clazz.getAnnotation(Mapping.class)).map(mapping -> {
			return Reflector.<String>invoke(Reflector.constInstance(mapping.mapper()), "map",
					Tool.array(Class.class, Field.class, String.class), clazz, field,
					fieldAnnotation == null ? null : mapping.value());
		}).orElseGet(field::getName);
    }

    /**
     * @param clazz instance or class
     * @return table name
     */
    public static String mappingClassName(Object clazz) {
        Class<?> start = clazz instanceof Class ? (Class<?>) clazz : clazz.getClass();
        for (Class<?> c = start; c != Object.class; c = c.getSuperclass()) {
            Mapping mapping = c.getAnnotation(Mapping.class);
            if (mapping != null) {
		return Reflector.invoke(Reflector.constInstance(mapping.mapper()), "map",
			Tool.array(Class.class, Field.class, String.class),
			clazz, null, mapping.value());
            }
        }
	return start.getSimpleName();
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
     * @param enumValue Enum value
     * @param annotationClass annotation class
     * @return annotation
     */
    public static <T extends Annotation> Optional<T> annotation(Enum<?> enumValue, Class<T> annotationClass) {
        return field(enumValue.getDeclaringClass(), enumValue.name()).map(f -> f.getAnnotation(annotationClass));
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
        return (Class<?>) getGenericParameters(field)[index];
    }

    /**
     * @param field Field
     * @return Generic type
     */
    public static Type[] getGenericParameters(Field field) {
        Type type = field.getGenericType();
        return type instanceof ParameterizedType ? ((ParameterizedType) type).getActualTypeArguments() : Tool.array();
    }

    /**
     * @param <T> Return type
     * @param clazz Class
     * @param index Index
     * @return Generic type
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getGenericParameter(Class<?> clazz, int index) {
        return (Class<T>) getGenericParameters(clazz)[index];
    }

    /**
     * @param <T> Return type
     * @param clazz Class
     * @return Generic type
     */
    public static <T> Type[] getGenericParameters(Class<?> clazz) {
        Type type = clazz.getGenericSuperclass();
        return type instanceof ParameterizedType ? ((ParameterizedType) type).getActualTypeArguments() : Tool.array();
    }

    /**
     * @param parameter Parameter
     * @return Generic parameters
     */
    public static Type[] getGenericParameters(Parameter parameter) {
        return Tool.of(parameter.getParameterizedType()).filter(i -> i instanceof ParameterizedType).map(i -> (ParameterizedType) i)
                .map(ParameterizedType::getActualTypeArguments).orElseGet(Tool::array);
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
            return null;
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
            return (int) value != 0;
        }
        if (value instanceof Long) {
            return (long) value != 0;
        }
        if (value instanceof Float) {
            return (float) value != 0;
        }
        if (value instanceof Double) {
            return (double) value != 0;
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
	 * @param accessField access field if not exists property
     * @return Property value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getProperty(Object instance, String name, Supplier<T> orElse, boolean accessField) {
        if (instance != null) {
            Class<?> clazz = instance.getClass();
            Method m = Tool.string(name).flatMap(i -> method(clazz, "get" + Character.toUpperCase(i.charAt(0)) + i.substring(1)))
                    .orElseGet(() -> name.startsWith("is") ? method(clazz, name).orElse(null) : null);
            if (m != null) {
                try {
                    return (T) m.invoke(instance);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    Log.warning(e, () -> "get property error");
                }
            } else if(accessField) {
            	return field(clazz, name).map(Try.f(field -> (T)field.get(instance), (e, field) -> {Log.warning(e, () -> "get property error");return null;})).orElseGet(orElse);
            }
        }
        return orElse.get();
    }

	/**
	 * @param instance instance
	 * @param type property type
	 * @param name property name
	 * @param value property value
	 * @param accessField access field if not exists property
	 */
	public static void setProperty(Object instance, Class<?> type, String name, Object value, boolean accessField) {
		if (instance != null) {
            Class<?> clazz = instance.getClass();
            Method m = Tool.string(name).flatMap(i -> method(clazz, "set" + Character.toUpperCase(i.charAt(0)) + i.substring(1), type)).orElse(null);
            if (m != null) {
                try {
                    m.invoke(instance, value);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    Log.warning(e, () -> "set property error");
                }
            } else if(accessField) {
            	field(clazz, name).ifPresent(Try.c(field -> field.set(instance, value), (e, field) -> Log.warning(e, () -> "set property error")));
            }
		}
	}

    /**
     * @param <T> Return type
     * @param method Method
     * @param args Arguments
     * @return Return value
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Method method, Object... args) {
        return (T)Try.s(() -> method.invoke(Modifier.isStatic(method.getModifiers()) ? null : instance(method.getDeclaringClass()), args)).get();
    }

    /**
     * Static method invoke
     * 
     * @param <T> Return type
     * @param methodFullName Full method name
     * @param argClasses Argument classes
     * @param args Arguments
     * @return Return value
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodFullName, Class<?>[] argClasses, Object... args) {
        int i = methodFullName.lastIndexOf('.');
        return i < 0 ? null
                : (T) clazz(methodFullName.substring(0, i)).flatMap(c -> method(c, methodFullName.substring(i + 1), argClasses))
                        .map(Try.f(m -> m.invoke(null, args))).orElse(null);
    }

    /**
     * @param            <T> Return type
     * @param instance   Instance
     * @param name       Method name
     * @param argClasses Argument classes
     * @param args       Arguments
     * @return Return value
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object instance, String name, Class<?>[] argClasses, Object... args) {
        return (T) method(instance.getClass(), name, argClasses).map(Try.f(m -> m.invoke(instance, args))).orElse(null);
    }

    /**
     * @param clazz Target class
     * @param annotation Target annotation
     * @param value New value
     */
    @SuppressWarnings("unchecked")
    public static void chagneAnnotation(Class<?> clazz, Class<? extends Annotation> annotation, Annotation value) {
	Object data = invoke(clazz, "annotationData", Tool.array());
        field(data.getClass(), "annotations").map(Try.f(f -> Map.class.cast(f.get(data)))).ifPresent(map -> map.put(annotation, value));
    }

    /**
     * @param field Target field
     * @param annotation Target annotation
     * @param value New value
     */
    public static void chagneAnnotation(Field field, Class<? extends Annotation> annotation, Annotation value) {
        Reflector.<Map<Class<? extends Annotation>, Annotation>>invoke(field, "declaredAnnotations", Tool.array()).put(annotation, value);
    }

    /**
     * @param executable Target method or constructor
     * @param annotation Target annotation
     * @param value New value
     */
    @SuppressWarnings("unchecked")
    public static void chagneAnnotation(Executable executable, Class<? extends Annotation> annotation, Annotation value) {
        method(Executable.class, "declaredAnnotations").map(Try.f(m -> Map.class.cast(m.invoke(executable)))).ifPresent(map -> map.put(annotation, value));
    }

    /**
     * @param <T> target class type
     * @param <R> annotation class type
     * @param targetClass target class
     * @param annotationClass annotation class
     * @return annotation
     */
    public <T, R extends Annotation> Optional<R> annotation(Class<T> targetClass, Class<R> annotationClass) {
    	return Tool.or(targetClass.getAnnotation(annotationClass), () -> Stream.of(targetClass.getAnnotations()).map(i -> i.getClass().getAnnotation(annotationClass)).findFirst().orElse(null));
    }
}
