package framework;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.el.ELProcessor;

/**
 * formatter with el, config, message
 */
public class Formatter implements AutoCloseable {

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Formatter.class.getCanonicalName());

    /**
     * current formatter
     */
    static final ThreadLocal<Formatter> current = new ThreadLocal<>();

    /**
     * target text
     */
    StringBuilder source = new StringBuilder();

    /**
     * current index
     */
    int index;

    /**
     * last index
     */
    int lastIndex;

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
        if (formatter.eat("<!--") && !(formatter.index < formatter.lastIndex && formatter.source.charAt(formatter.index) == '{')) {
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
     * HTML escape(&amp;, &quot;, &lt;, &gt;, &#39;)
     *
     * @param text target
     * @return escaped text
     */
    public static String htmlEscape(Object text) {
        return Tool.string(text).map(i -> i.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;"))
                .orElse(null);
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
     * map
     */
    Map<String, Object> map;

    /**
     * values
     */
    Object[] values;

    /**
     * constructor
     *
     * @param exclude exclude expression
     * @param escape escape text
     * @param map map
     * @param values values
     */
    Formatter(Function<Formatter, Result> exclude, Function<Object, String> escape, Map<String, Object> map, Object... values) {
        this.exclude = exclude;
        this.escape = escape;
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
        Formatter result = new Formatter(exclude, escape, map, values);
        result.el = el;
        result.cache = cache;
        return result;
    }

    /**
     * @param text target
     * @return formatted text
     */
    public String format(String text) {
        if (text == null) {
            return null;
        }
        source.setLength(0);
        source.append(text);
        index = 0;
        lastIndex = source.length();
        braces.clear();
        while (index < lastIndex) {
            skipSpaces();
            if (exclude != null && braces.isEmpty()) {
                Result b = exclude.apply(this);
                if (b == Result.EXIT) {
                    return source.toString();
                }
                if (b == Result.SKIP) {
                    continue;
                }
            }
            if (eat("{")) {
                int prefix = 1;
                if (prev("<!--{")) {
                    prefix = 5;
                } else if (prev("/*{") || eat("/*")) {
                    prefix = 3;
                } else if (prev("${") || prev("#{")) {
                    prefix = 2;
                }
                braces.push(index - prefix);
                continue;
            }
            if (eat("}") && !braces.isEmpty()) {
                int start = braces.pop();
                int first = source.charAt(start);
                int prefix = first == '$' || first == '#' ? 2 : 1;
                int suffix = 1;
                switch (first) {
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
                        if (source.charAt(start + 1) == '/') {
                            prefix = "{/*".length();
                            suffix = "*/}".length();
                        }
                        break;
                }
                int end = index;
                if (start + prefix < end - suffix) {
                    String before = source.substring(start, end);
                    String after = Tool.string(eval(before, prefix, suffix)).orElse("");
                    source.replace(start, end, after);
                    lastIndex = source.length();
                    index = end + after.length() - before.length();
                }
                continue;
            }
            index++;
        }
        return source.toString();
    }

    /**
     * skip spaces
     */
    void skipSpaces() {
        for (; index < lastIndex; index++) {
            switch (source.charAt(index)) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    continue;
            }
            break;
        }
    }

    /**
     * skip until a letter
     *
     * @param letter a letter
     * @return true:found a letter, false:not found
     */
    boolean skipUntil(int letter) {
        for (; index < lastIndex; index++) {
            if (letter == source.charAt(index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * eat a word
     *
     * @param word word
     * @return true:ate a word, false:not ate
     */
    boolean eat(String word) {
        int length = word.length();
        if (index + length <= lastIndex && source.substring(index, index + length).equals(word)) {
            index += length;
            return true;
        }
        return false;
    }

    /**
     * prev word check
     *
     * @param word word
     * @return true:equals prev word, false:not equals
     */
    boolean prev(String word) {
        int length = word.length();
        return index - length >= 0 && source.substring(index - length, index).equals(word);
    }

    /**
     * index of word from current index
     *
     * @param word word
     * @return index of word, -1:not found
     */
    int indexOf(String word) {
        return source.indexOf(word, index);
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
     * @param text target({key} replace messages, ${expression} replace el value
     * with escape, #{expression} replace el value with no escape)
     * @param exclude exclude
     * @param escape escape
     * @param map ${key} replace to value
     * @param values {0}, {1}... replace to value
     * @return result text
     */
    public static String format(String text, Function<Formatter, Result> exclude, Function<Object, String> escape, Map<String, Object> map, Object... values) {
        try (Formatter formatter = new Formatter(exclude, escape, map, values)) {
            return formatter.format(text);
        }
    }

    /**
     * @param path include file path
     * @return content
     */
    public static String include(String path) {
        try {
            return current.get().copy().format(
                    Tool.loadText(Config.toURL(path).orElseThrow(FileNotFoundException::new).openStream()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param expression expression
     * @param prefix prefix length
     * @param suffix suffix length
     * @return result
     */
    String eval(String expression, int prefix, int suffix) {
        return cache.computeIfAbsent(expression, s -> {
            boolean isEl = !(s.startsWith("{") && prefix == 1);
            boolean isEscape = !isEl || (s.startsWith("${") && prefix == 2);
            BiFunction<Object, String, String> getResult = (result, type) -> {
                String value;
                if (escape != null && isEscape) {
                    value = escape.apply(result);
                } else {
                    value = Tool.string(result).orElse(null);
                    type = "raw " + type;
                }
                logger.config("[" + type + "] " + s + " -> " + value);
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
                    if (el == null) {
                        el = new ELProcessor();
                        el.defineFunction("F", "include", getClass().getMethod("include", String.class));
                        // el.defineFunction("F", "includeIf",
                        // Tool.class.getMethod("includeIf", boolean.class,
                        // String.class, Object.class, Object[].class));
                        // el.defineFunction("F", "includeN",
                        // Tool.class.getMethod("includeN", int.class,
                        // String.class, Object.class, Object[].class));
                        // el.defineFunction("F", "includeFor",
                        // Tool.class.getMethod("includeFor", Stream.class,
                        // String.class, Object.class, Object[].class));
                        // el.defineFunction("F", "json",
                        // Tool.class.getMethod("json", Object.class,
                        // boolean.class));
                        el.defineBean("C", Config.properties);
                        el.defineBean("M", Message.messages);
                        el.defineBean("V", values == null ? new Object[]{} : values);
                        el.defineBean("A", Application.current().orElse(null));
                        el.defineBean("S", Session.current().orElse(null));
                        el.defineBean("R", Request.current().orElse(null));
                        if (map != null) {
                            map.forEach(el::defineBean);
                        }
                    }
                    return getResult.apply(Tool.string(el.eval(key)).orElse(""), "el");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "el error", e);
                    return s;
                }
            }

            /* bind values {0}... */
            if (key.matches("^[0-9]+$")) {
                int i = Integer.parseInt(key);
                if (values != null && i < values.length) {
                    return getResult.apply(values[i] == null ? null : values[i].toString(), "values");
                } else {
                    return s;
                }
            }

            /* bind messages {message.id:parameter1:...} */
            if (key.indexOf('\n') < 0) {
                String[] keys = key.split("\\s*:\\s*");
                boolean hasParameter = keys.length > 1;
                if (hasParameter) {
                    key = keys[0];
                }
                Optional<String> message = Message.find(key);
                if (message.isPresent()) {
                    return getResult.apply(hasParameter ? new MessageFormat(message.get()).format(Arrays.copyOfRange(keys, 1, keys.length)) : message.get(),
                            "message");
                }
            }
            return s;
        });
    }

    @Override
    public void close() {
        if (current.get() == this) {
            current.remove();
            current.set(null);
        }
    }
}
