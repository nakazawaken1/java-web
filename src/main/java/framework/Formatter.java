package framework;

import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.time.chrono.JapaneseDate;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELProcessor;
import javax.el.ELResolver;
import javax.el.LambdaExpression;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.el.StandardELContext;

import app.config.Sys;
import framework.annotation.Config;

/**
 * formatter with el, config, message
 */
public class Formatter extends AbstractParser implements AutoCloseable {

    /**
     * current formatter
     */
    static final ThreadLocal<Formatter> current = new ThreadLocal<>();

    /**
     * elClass entries
     */
    public static Map<String, Class<?>> elClassMap = Tool.map("Sys", Sys.class, "Tool", Tool.class, "JapaneseDate", JapaneseDate.class);

    /**
     * cache enabled
     */
    boolean isCache = true;

    /**
     * index of {
     */
    Deque<Integer> braces = new LinkedList<>();

    /**
     * result action
     */
    public enum Result {
        /**
         * Exit process
         */
        EXIT,
        /**
         * Skip process
         */
        SKIP,
        /**
         * Succeeded
         */
        NEXT,
    }

    /**
     * exclude for JavaScript(line comment, block comment, single quote)
     *
     * @param formatter formatter
     * @return result
     */
    public static Result excludeForScript(Formatter formatter) {
        if (formatter.eat("//")) {
            formatter.index = formatter.indexOf("\n");
            if (formatter.index < 0) {
                return Result.EXIT;
            }
            formatter.index += "\n".length();
            return Result.SKIP;
        }
        if (formatter.eat("/*")) {
            formatter.index = formatter.indexOf("*/");
            if (formatter.index < 0) {
                return Result.EXIT;
            }
            formatter.index += "*/".length();
            return Result.SKIP;
        }
        if (formatter.eat("'")) {
            for (;;) {
                if (!formatter.skipUntil('\'')) {
                    return Result.EXIT;
                }
                formatter.index++;
                if (!formatter.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * exclude for css(block comment, single quote)
     *
     * @param formatter formatter
     * @return result
     */
    public static Result excludeForStyle(Formatter formatter) {
        if (formatter.eat("/*") && formatter.charAt(formatter.index) != '{') {
            formatter.index = formatter.indexOf("*/");
            if (formatter.index < 0) {
                return Result.EXIT;
            }
            formatter.index += "*/".length();
            return Result.SKIP;
        }
        if (formatter.eat("'")) {
            for (;;) {
                if (!formatter.skipUntil('\'')) {
                    return Result.EXIT;
                }
                formatter.index++;
                if (!formatter.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * exclude for sql(line comment, single quote)
     *
     * @param formatter formatter
     * @return result
     */
    public static Result excludeForSql(Formatter formatter) {
        if (formatter.eat("--")) {
            formatter.index = formatter.indexOf("\n");
            if (formatter.index < 0) {
                return Result.EXIT;
            }
            formatter.index += "\n".length();
            return Result.SKIP;
        }
        if (formatter.eat("'")) {
            for (;;) {
                if (!formatter.skipUntil('\'')) {
                    return Result.EXIT;
                }
                formatter.index++;
                if (!formatter.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * exclude for html(comment, single quote)
     *
     * @param formatter formatter
     * @return result
     */
    public static Result excludeForHtml(Formatter formatter) {
        if (formatter.eat("<!--") && !(formatter.index < formatter.lastIndex && formatter.charAt(formatter.index) == '{')) {
            formatter.index = formatter.indexOf("-->");
            if (formatter.index < 0) {
                return Result.EXIT;
            }
            formatter.index += "-->".length();
            return Result.SKIP;
        }
        if (formatter.eat("'")) {
            for (;;) {
                if (!formatter.skipUntil('\'')) {
                    return Result.EXIT;
                }
                formatter.index++;
                if (!formatter.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * javascript escape(\n : \\n)
     *
     * @param text target
     * @return escaped text
     */
    public static String scriptEscape(Object text) {
        return Tool.string(text).map(i -> i.replace("\n", "\\n")).orElse(null);
    }

    /**
     * exclude expression
     */
    Function<Formatter, Result> exclude;

    /**
     * escape text
     */
    Function<Object, String> escape;

    /**
     * locale
     */
    Locale locale;

    /**
     * map
     */
    Map<String, ?> map;

    /**
     * values
     */
    Object[] values;

    /**
     * constructor
     *
     * @param exclude exclude expression
     * @param escape escape text
     * @param locale locale
     * @param map map
     * @param values values
     */
    Formatter(Function<Formatter, Result> exclude, Function<Object, String> escape, Locale locale, Map<String, ?> map, Object... values) {
        this.exclude = exclude;
        this.escape = escape;
        this.locale = locale;
        this.map = map;
        this.values = values;
        if (current.get() == null) {
            current.set(this);
        }
    }

    /**
     * @return formatter
     */
    Formatter copy() {
        Formatter result = new Formatter(exclude, escape, locale, map, values);
        // result.el = el;
        result.cache = cache;
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see framework.AbstractParser#set(java.lang.String)
     */
    @Override
    void set(String text) {
        braces.clear();
        super.set(text);
    }

    /**
     * @param text target
     * @return formatted text
     */
    public String format(String text) {
        if (text == null) {
            return null;
        }
        set(text);
        while (index < lastIndex) {
            skipSpaces();
            if (exclude != null && braces.isEmpty()) {
                Result b = exclude.apply(this);
                if (b == Result.EXIT) {
                    return toString();
                }
                if (b == Result.SKIP) {
                    continue;
                }
            }
            if (eat("{")) {
                int prefix = 1;
                if(prev("data-el=\"{")) {
                    prefix = "data-el=\"{".length();
                } else if (prev("<!--{")) {
                    prefix = "<!--{".length();
                } else if (prev("/*{") || eat("/*")) {
                    prefix = "/*{".length();
                } else if (prev("${") || prev("#{")) {
                    prefix = "${".length();
                }
                braces.push(index - prefix);
                continue;
            }
            if (eat("}") && !braces.isEmpty()) {
                int start = braces.pop();
                int first = charAt(start);
                int prefix = first == '$' || first == '#' ? 2 : 1;
                int suffix = 1;
                switch (first) {
                case 'd':
                    eat("\"");
                    prefix = "data-el=\"{".length();
                    suffix = "}\"".length();
                    break;
                case '<':
                    eat("-->");
                    prefix = "<!--{".length();
                    suffix = "}-->".length();
                    break;
                case '/':
                    eat("*/");
                    prefix = "/*{".length();
                    suffix = "}*/".length();
                    break;
                default:
                    if (charAt(start + 1) == '/') {
                        prefix = "{/*".length();
                        suffix = "*/}".length();
                    }
                    break;
                }
                int end = index;
                if (start + prefix < end - suffix) {
                    String before = subSequence(start, end).toString();
                    String after = Tool.string(eval(before, prefix, suffix)).orElse("");
                    replace(start, end, after);
                    index = end + after.length() - before.length();
                }
                continue;
            }
            index++;
        }
        return toString();
    }

    /**
     * el processor
     */
    ELProcessor el = null;
    /**
     * evaluate cache
     */
    Map<String, String> cache = new HashMap<>();

    /**
     * format
     *
     * @param text target({key} replace messages, ${expression} replace el value with escape, #{expression} replace el value with no escape)
     * @param exclude exclude
     * @param escape escape
     * @param locale locale
     * @param map ${key} replace to value
     * @param values {0}, {1}... replace to value
     * @return result text
     */
    public static String format(String text, Function<Formatter, Result> exclude, Function<Object, String> escape, Locale locale, Map<String, ?> map,
            Object... values) {
        try (Formatter formatter = new Formatter(exclude, escape, locale, map, values)) {
            for(;;) {
                String text0 = text;
                text = formatter.format(text);
                if(text0.equals(text)) {
                    break;
                }
            }
            return text;
        }
    }

    /**
     * @param path Include file path
     * @return Content
     */
    public static String include(String path) {
        return Tool.toURL(path).map(url -> {
            return current.get().copy().format(Tool.using(url::openStream, Tool::loadText));
        }).orElse("((not found: " + path + "))");
    }

    /**
     * @param path Include file path
     * @param values Values
     * @return Content
     */
    public static String include(String path, List<Object> values) {
        return Tool.toURL(path).map(url -> {
            return Tool.peek(current.get().copy(), c -> c.values = values.toArray()).format(Tool.using(url::openStream, Tool::loadText));
        }).orElse("((not found: " + path + "))");
    }

    /**
     * @param path Include file path
     * @param list List
     * @return Content
     */
    public static String includeFor(String path, Iterable<?> list) {
        return Tool.toURL(path).map(url -> {
            String text = Tool.using(url::openStream, Tool::loadText);
            Formatter formatter = current.get().copy();
            boolean backup = formatter.isCache;
            formatter.isCache = false;
            StringBuilder s = new StringBuilder();
            list.forEach(i -> {
                formatter.el().setValue("I", i);
                s.append(formatter.format(text));
            });
            formatter.isCache = backup;
            return s.toString();
        }).orElse("((not found: " + path + "))");
    }

    /**
     * @param path Include file path
     * @param condition Include if true
     * @return Content
     */
    public static String includeIf(String path, boolean condition) {
        return Tool.toURL(path).map(url -> {
            if (condition) {
                Formatter formatter = current.get().copy();
                boolean backup = formatter.isCache;
                formatter.isCache = false;
                String result = formatter.format(Tool.using(url::openStream, Tool::loadText));
                formatter.isCache = backup;
                return result;
            }
            return "";
        }).orElse("((not found: " + path + "))");
    }

    /**
     * @param expression expression
     * @param prefix prefix length
     * @param suffix suffix length
     * @return result
     */
    String eval(String expression, int prefix, int suffix) {
        Function<String, String> get = s -> {
            boolean isEl = !(s.startsWith("{") && prefix == 1);
            boolean isEscape = !isEl || s.startsWith("${") && prefix == 2;
            BiFunction<Object, String, String> getResult = (result, type) -> {
                String value;
                if (escape != null && isEscape) {
                    value = escape.apply(result);
                } else {
                    value = Tool.string(result).orElse(null);
                    type = "raw " + type;
                }
                if (value != null && !isEl) {
                    value = value.replaceAll("\n", "<br/>\n");
                }
                Log.config("[" + type + "] " + s + " -> " + Tool.cut(value, Sys.Log.eval_max_letters, " ..."));
                return value;
            };
            String key = s.substring(prefix, s.length() - suffix);
            if (isEl) {
                /* bind map */
                if (map != null && map.containsKey(key)) {
                    return getResult.apply(map.get(key), "map");
                }

                /* bind el */
                try {
                    return getResult.apply(Tool.string(el().eval(key)).orElse(""), "el");
                } catch(PropertyNotFoundException e) {
                    Log.warning(e.toString());
                    return null;
                } catch (Exception e) {
                    Log.warning(e, () -> "el error");
                    return s;
                }
            }

            /* bind values {0}... */
            if (key.matches("^[0-9]+$")) {
                int i = Integer.parseInt(key);
                if (values != null && i < values.length) {
                    return getResult.apply(values[i], "values");
                } else {
                    return s;
                }
            }

            /* bind config {key:parameter1:...} */
            if (key.indexOf('\n') < 0) {
                String[] keys = key.split("\\s*:\\s*");
                boolean hasParameter = keys.length > 1;
                String realKey = hasParameter ? keys[0] : key;
                Optional<String> message = Config.Injector.getValue(realKey, locale);
                if (message.isPresent()) {
                    return getResult.apply(hasParameter ? new MessageFormat(message.get()).format(Arrays.copyOfRange(keys, 1, keys.length)) : message.get(),
                            "config");
                }
                Log.info("not found config: " + realKey);
            }
            return s;
        };
        return isCache ? cache.computeIfAbsent(expression, get) : Tool.of(cache.get(expression)).orElseGet(() -> get.apply(expression));
    }

    /**
     * @return ELProcessor
     */
    private ELProcessor el() {
        if (el == null) {
            el = new ELProcessor();
            el.getELManager().addELResolver(new ELResolver() { /* top level empty, Optional.map, Optional.flatMap, Optional.orElseGet resolver */

                @Override
                public Object getValue(ELContext context, Object base, Object property) {
                    try {
                        ELContext c = Reflector.method(context.getClass(), "getELContext").map(Try.f(method -> (ELContext) method.invoke(context)))
                                .orElse(context);
                        if (c instanceof StandardELContext && base == null && property instanceof String && Reflector
                                .method(StandardELContext.class, "getBeans").map(Try.f(m -> !((Map<?, ?>) m.invoke(c)).containsKey(property))).orElse(false)) {
                            context.setPropertyResolved(true);
                        }
                    } catch (SecurityException | IllegalArgumentException e) {
                        return null;
                    }
                    return null;
                }

                @Override
                public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
                    if (base instanceof Optional && method instanceof String && params.length == 1 && params[0] instanceof LambdaExpression) {
                        LambdaExpression lambda = (LambdaExpression) params[0];
                        @SuppressWarnings("unchecked")
                        Optional<Object> o = (Optional<Object>) base;
                        switch ((String) method) {
                        case "filter":
                            context.setPropertyResolved(true);
                            return o.filter(e -> (boolean)lambda.invoke(context, e));
                        case "map":
                            context.setPropertyResolved(true);
                            return o.map(e -> lambda.invoke(context, e));
                        case "flatMap":
                            context.setPropertyResolved(true);
                            return o.flatMap(e -> (Optional<?>) lambda.invoke(context, e));
                        case "ifPresent":
                            context.setPropertyResolved(true);
                            o.ifPresent(e -> lambda.invoke(context, e));
                            return null;
                        case "orElseGet":
                            context.setPropertyResolved(true);
                            return o.orElseGet(() -> lambda.invoke(context));
                        }
                    }
                    return super.invoke(context, base, method, paramTypes, params);
                }

                @Override
                public Class<?> getType(ELContext context, Object base, Object property) {
                    return null;
                }

                @Override
                public void setValue(ELContext context, Object base, Object property, Object value) {
                }

                @Override
                public boolean isReadOnly(ELContext context, Object base, Object property) {
                    return true;
                }

                @Override
                public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
                    return Arrays.<FeatureDescriptor>asList().iterator();
                }

                @Override
                public Class<?> getCommonPropertyType(ELContext context, Object base) {
                    return String.class;
                }
            });
            el.getELManager().addELResolver(new ELResolver() { /* inner class resolver */

                @Override
                public Object getValue(ELContext context, Object base, Object property) {
                    try {
                        if (base instanceof ELClass && property instanceof String) {
                            Class<?> clazz = ((ELClass) base).getKlass();
                            return Stream.of(clazz.getClasses()).filter(c -> c.getSimpleName().equals(property)).peek(c -> context.setPropertyResolved(true))
                                    .findFirst().map(ELClass::new).orElse(null);
                        }
                    } catch (SecurityException | IllegalArgumentException e) {
                        return null;
                    }
                    return null;
                }

                @Override
                public Class<?> getType(ELContext context, Object base, Object property) {
                    try {
                        if (base instanceof ELClass && property instanceof String) {
                            Class<?> clazz = ((ELClass) base).getKlass();
                            return Stream.of(clazz.getClasses()).filter(c -> c.getSimpleName().equals(property)).peek(c -> context.setPropertyResolved(true))
                                    .findFirst().orElse(null);
                        }
                    } catch (SecurityException | IllegalArgumentException e) {
                        return null;
                    }
                    return null;
                }

                @Override
                public void setValue(ELContext context, Object base, Object property, Object value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean isReadOnly(ELContext context, Object base, Object property) {
                    return true;
                }

                @Override
                public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
                    return Arrays.<FeatureDescriptor>asList().iterator();
                }

                @Override
                public Class<?> getCommonPropertyType(ELContext context, Object base) {
                    return String.class;
                }
            });
            el.getELManager().addELResolver(new ELResolver() { /* field resolver */

                @Override
                public void setValue(ELContext context, Object base, Object property, Object value) {
                    try {
                        if (base != null && property instanceof String) {
                            Method method;
                            try {
                                method = new PropertyDescriptor((String) property, base.getClass()).getWriteMethod();
                            } catch (IntrospectionException e) {
                                method = null;
                            }
                            if (method != null) {
                                method.invoke(base, value);
                            } else {
                                Field f = base.getClass().getDeclaredField((String) property);
                                int m = f.getModifiers();
                                if (Modifier.isFinal(m)) {
                                    throw new PropertyNotWritableException((String) property);
                                }
                                f.set(base, value);
                            }
                            context.setPropertyResolved(true);
                        }
                    } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                        throw new PropertyNotWritableException((String) property);
                    }
                }

                @Override
                public boolean isReadOnly(ELContext context, Object base, Object property) {
                    try {
                        if (base != null && property instanceof String) {
                            boolean result = new PropertyDescriptor((String) property, base.getClass()).getWriteMethod() == null
                                    || Modifier.isFinal(base.getClass().getDeclaredField((String) property).getModifiers());
                            context.setPropertyResolved(true);
                            return result;
                        }
                    } catch (NoSuchFieldException | SecurityException | IntrospectionException e) {
                        return true;
                    }
                    return false;
                }

                @Override
                public Object getValue(ELContext context, Object base, Object property) {
                    try {
                        if (base != null && property instanceof String) {
                            Class<?> clazz = base.getClass();
                            Method method;
                            try {
                                method = new PropertyDescriptor((String) property, clazz).getReadMethod();
                            } catch (IntrospectionException e) {
                                method = null;
                            }
                            Object value;
                            if (method != null) {
                                value = method.invoke(base);
                            } else {
                                Field f = Reflector.field(clazz, (String) property).orElseThrow(() -> new NoSuchFieldException(clazz.getSimpleName() + "." + property));
                                value = f.get(base);
                            }
                            context.setPropertyResolved(true);
                            return value;
                        }
                    } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                        return null;
                    }
                    return null;
                }

                @Override
                public Class<?> getType(ELContext context, Object base, Object property) {
                    try {
                        if (base != null && property instanceof String) {
                            Class<?> c;
                            try {
                                c = new PropertyDescriptor((String) property, base.getClass()).getPropertyType();
                            } catch (IntrospectionException e) {
                                c = null;
                            }
                            if (c == null) {
                                c = base.getClass().getDeclaredField((String) property).getType();
                            }
                            context.setPropertyResolved(true);
                            return c;
                        }
                    } catch (NoSuchFieldException | SecurityException e) {
                        return null;
                    }
                    return null;
                }

                @Override
                public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
                    return Arrays.<FeatureDescriptor>asList().iterator();
                }

                @Override
                public Class<?> getCommonPropertyType(ELContext context, Object base) {
                    return base == null ? String.class : Object.class;
                }
            });
            el.defineBean("A", Application.current().orElse(null));
            try {
                el.defineFunction("F", "include", Reflector.method(Formatter.class, "include", String.class).get());
                el.defineFunction("F", "includeV", Reflector.method(Formatter.class, "include", String.class, List.class).get());
                el.defineFunction("F", "includeFor", Reflector.method(Formatter.class, "includeFor", String.class, Iterable.class).get());
                el.defineFunction("F", "includeIf", Reflector.method(Formatter.class, "includeIf", String.class, boolean.class).get());
            } catch (NoSuchMethodException e) {
                throw new InternalError(e);
            }
            el.defineBean("P", Sys.context_path);
            el.defineBean("R", Request.current().orElse(null));
            el.defineBean("S", Session.current().orElse(null));
            el.defineBean("V", Tool.list(values));
            elClassMap.forEach((k, v) -> el.defineBean(k, new ELClass(v)));
            if (map != null) {
                map.forEach((k, v) -> el.defineBean(k, v == null ? "" : v));
            }
        }
        return el;
    }

    @Override
    public void close() {
        if (current.get() == this) {
            current.remove();
            current.set(null);
        }
    }
}
