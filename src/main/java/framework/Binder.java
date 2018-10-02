package framework;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import framework.AbstractValidator.ErrorAppender;

/**
 * Binder
 */
public class Binder implements ErrorAppender {
    /**
     * Parameters
     */
    Map<String, List<String>> parameters;

    /**
     * Files
     */
    Map<String, Tuple<byte[], File>> files;

    /**
     * Validator(validatee, errors)
     */
    Consumer<String> validator;

    /**
     * Errors
     */
    Map<String, List<String>> errors = new LinkedHashMap<>();

    /**
     * @param parameters parameters
     */
    public Binder(Map<String, List<String>> parameters) {
        this.parameters = parameters;
    }

    /**
     * @param files Files
     * @return Self
     */
    public Binder files(Map<String, Tuple<byte[], File>> files) {
        this.files = files;
        return this;
    }

    /**
     * @param validator Validator
     * @return Self
     */
    public Binder validator(Consumer<String> validator) {
        this.validator = validator;
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see framework.AbstractValidator.ErrorAppender#addError(java.lang.String, java.lang.String, java.lang.String, java.lang.Object[])
     */
    @Override
    public void addError(String name, String value, String error, Object... keyValues) {
        Tool.addValue(errors, name, Formatter
            .format(error, Formatter::excludeForHtml, Tool::htmlEscape, Session.currentLocale(), Tool.map("validatedValue", value, keyValues)));
    }

    /**
     * @param clazz class
     * @param text text
     * @param error action if error(allow null:retry that text is "0")
     * @return value
     */
    Object convert(String text, Type clazz, Function<Exception, Object> error) {
        if (validator != null) {
            validator.accept(text);
        }
        Function<Function<String, Object>, Object> toNumber = f -> Try
            .s(() -> f.apply(text), error == null ? (Function<Exception, Object>) (e -> f.apply("0")) : error)
            .get();
        if (clazz == String.class) {
            return text == null ? error == null ? "" : error.apply(null) : text;
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            if (text != null) {
                Optional<Integer> n = Tool.integer(text);
                if (text.equalsIgnoreCase("false") || n.filter(i -> i == 0)
                    .isPresent()) {
                    return false;
                }
                if (text.equalsIgnoreCase("true") || n.filter(i -> i != 0)
                    .isPresent()) {
                    return true;
                }
            }
            return error == null ? false : error.apply(null);
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
        if (clazz == LocalDate.class) {
            return Try.<String, LocalDate>f(LocalDate::parse, (e, s) -> error == null ? null : (LocalDate) error.apply(null))
                .apply(text);
        }
        if (clazz == LocalDateTime.class) {
            return Try.<String, LocalDateTime>f(LocalDateTime::parse, (e, s) -> error == null ? null : (LocalDateTime) error.apply(null))
                .apply(text);
        }
        if (clazz == LocalTime.class) {
            return Try.<String, LocalTime>f(LocalTime::parse, (e, s) -> error == null ? null : (LocalTime) error.apply(null))
                .apply(text);
        }
        if (Enum.class.isAssignableFrom((Class<?>) clazz)) {
            return text != null ? Reflector.invoke(((Class<?>) clazz).getName() + ".valueOf", Tool.array(String.class), text)
                    : error == null ? ((Class<?>) clazz).getEnumConstants()[0] : error.apply(null);
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
        return bind(parameters, 0, name, clazz, parameterizedType);
    }

    /**
     * @param nest Nest level
     * @param type Value type
     * @return Mapper
     */
    @SuppressWarnings("unchecked")
    public Function<Object, Object> rebind(int nest, Class<?> type) {
        return i -> {
            if (i instanceof Map) {
                if (type == Map.class) {
                    return ((Map<String, List<String>>) i).entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> Tool.<List<String>, String>val(e.getValue(), j -> j.isEmpty() ? "" : j.get(0))));
                }
                Object instance = Reflector.instance(type);
                Reflector.fields(type)
                    .forEach(Try.biC((n, field) -> {
                        field.set(instance, bind((Map<String, List<String>>) i, nest + 1, n, field.getType(), Reflector.getGenericParameters(field)));
                    }));
                return instance;
            }
            return convert((String) i, type, null);
        };
    }

    /**
     * @param parameters Parameters
     * @param nest Nest level
     * @param name name
     * @param clazz class
     * @param parameterizedType Parameterized type
     * @return value
     */
    @SuppressWarnings("unchecked")
    public Object bind(Map<String, List<String>> parameters, int nest, String name, Class<?> clazz, Type... parameterizedType) {
        if (clazz == null) {
            return null;
        }
        // List<String> values = Tool.or(parameters.get(name), () -> parameters.get(name + "[]")).orElse(null);
        List<Object> sub = new ArrayList<>();
        parameters.forEach((key, value) -> {
            int start = key.indexOf('[');
            int dot = key.indexOf('.');
            int min = start < 0 ? (dot < 0 ? key.length() : dot) : (dot < 0 ? start : Math.min(start, dot));
            String realKey = key.substring(0, min);
            if (!name.equals(realKey)) {
                return;
            }
            if (key.equals(realKey)) {
                sub.addAll(value);
            }
            if (start >= 0 && (dot == -1 || start < dot)) {
                int end = key.indexOf(']');
                if (start + 1 < end) {
                    int index = Integer.parseInt(key.substring(start + 1, end));
                    while (sub.size() <= index) {
                        sub.add(null);
                    }
                    Object o = sub.get(index);
                    if (dot > 0) {
                        if (!(o instanceof Map)) {
                            sub.set(index, o = new LinkedHashMap<>());
                        }
                        ((Map<String, List<String>>) o).put(key.substring(dot + 1), value);
                    }
                } else {
                    key = key.substring(end + 1);
                    if (key.isEmpty()) {
                        for (String i : value) {
                            sub.add(i);
                        }
                    } else {
                        sub.add(Tool.map(key, value));
                    }
                }
            }
        });
        String first = sub.isEmpty() ? Tool.getFirst(parameters, name)
            .orElse(null)
                : sub.get(0)
                    .toString();
        if (sub.size() == 1 && "".equals(first)) {
            sub.clear();
        }

        // Array
        Class<?> component = clazz.getComponentType();
        if (component != null) {
            Stream<Object> stream = sub.stream()
                .map(value -> convert((String) value, component, null));
            if (clazz == int[].class) {
                return stream.mapToInt(Integer.class::cast)
                    .toArray();
            }
            if (clazz == long[].class) {
                return stream.mapToLong(Long.class::cast)
                    .toArray();
            }
            if (clazz == double[].class) {
                return stream.mapToDouble(Double.class::cast)
                    .toArray();
            }
            if (clazz == boolean[].class) {
                Object[] from = stream.toArray();
                boolean[] to = new boolean[from.length];
                for (int i = 0, end = from.length; i < end; i++) {
                    to[i] = (boolean) from[i];
                }
                return to;
            }
            if (clazz == byte[].class) {
                if (sub.size() == 1 && files.containsKey(first)) {
                    return Tool.val(files.get(first), t -> t.l == null ? Try.f(Files::readAllBytes)
                        .apply(t.r.toPath()) : t.l);
                }
                Object[] from = stream.toArray();
                byte[] to = new byte[from.length];
                for (int i = 0, end = from.length; i < end; i++) {
                    to[i] = (byte) from[i];
                }
                return to;
            }
            if (clazz == short[].class) {
                Object[] from = stream.toArray();
                short[] to = new short[from.length];
                for (int i = 0, end = from.length; i < end; i++) {
                    to[i] = (short) from[i];
                }
                return to;
            }
            if (clazz == char[].class) {
                Object[] from = stream.toArray();
                char[] to = new char[from.length];
                for (int i = 0, end = from.length; i < end; i++) {
                    to[i] = (char) from[i];
                }
                return to;
            }
            if (clazz == float[].class) {
                Object[] from = stream.toArray();
                float[] to = new float[from.length];
                for (int i = 0, end = from.length; i < end; i++) {
                    to[i] = (float) from[i];
                }
                return to;
            }
            return sub.stream()
                .map(rebind(nest, component))
                .toArray(n -> (Object[]) Array.newInstance(component, n));
        }

        if (List.class.isAssignableFrom(clazz)) {
            return sub.stream()
                .map(rebind(nest, parameterizedType.length > 0 ? (Class<?>) parameterizedType[0] : Object.class))
                .collect(Collectors.toList());
        }

        if (Set.class.isAssignableFrom(clazz)) {
            return sub.stream()
                .map(rebind(nest, parameterizedType.length > 0 ? (Class<?>) parameterizedType[0] : Object.class))
                .collect(Collectors.toSet());
        }

        if (Map.class.isAssignableFrom(clazz)) {
            String prefix = name + ".";
            return parameters.entrySet()
                .stream()
                .filter(e -> e.getKey()
                    .startsWith(prefix))
                .collect(Attributes.Impl::new, (map, e) -> map.put(e.getKey()
                    .substring(prefix.length()), bind(parameters, nest + 1, e.getKey(), parameterizedType.length > 1 ? (Class<?>) parameterizedType[1]
                            : Object.class)), Map::putAll);
        }

        if (clazz == Optional.class) {
            Class<?> c = parameterizedType.length > 0 ? (Class<?>) parameterizedType[0] : Object.class;
            return c == String.class ? Tool.string(bind(parameters, nest + 1, name, c)) : Tool.of(bind(parameters, nest + 1, name, c));
        }

        if (!clazz.isPrimitive() && !Enum.class.isAssignableFrom(clazz) && !Tool.val(clazz.getName(), i -> Stream.of("java.", "com.sun.")
            .anyMatch(i::startsWith))) {
            Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            if (constructor.getParameterCount() > 0) {
                Object[] args = Stream.of(constructor.getParameters())
                    .map(p -> bind(name + "." + p.getName(), p.getType(), Reflector.getGenericParameters(p)))
                    .toArray();
                return Try.s(() -> constructor.newInstance(args))
                    .get();
            }
            Object o = Try.s(constructor::newInstance)
                .get();
            Reflector.fields(o.getClass())
                .forEach(Try.biC((fieldName, field) -> field.set(o, bind(name + "." + fieldName, field.getType(), Reflector.getGenericParameters(field)))));
            return o;
        }

        return convert(first, clazz, nest == 0 ? null : e -> null);
    }
}
