package framework;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.el.ELProcessor;

/**
 * ｛｝置換用パーサー
 */
public class Formatter {

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Formatter.class.getCanonicalName());

    /**
     * 置換対象文字列
     */
    StringBuilder source;

    /**
     * 現在位置
     */
    int index;

    /**
     * 最終位置
     */
    int lastIndex;

    /**
     * {の出現位置
     */
    Deque<Integer> braces;

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
     * JavaScript用(行コメント、ブロックコメント、シングルクォート文字列を除外)
     *
     * @param p パーサ
     * @return 結果
     */
    public static Result excludeForScript(Formatter p) {
        if (p.eat("//")) {
            p.index = p.indexOf("\n");
            if (p.index < 0) {
                return Result.EXIT;
            }
            p.index++;
            return Result.SKIP;
        }
        if (p.eat("/*")) {
            p.index = p.indexOf("*/");
            if (p.index < 0) {
                return Result.EXIT;
            }
            p.index += 2;
            return Result.SKIP;
        }
        if (p.eat("'")) {
            for (;;) {
                if (!p.skipUntil('\'')) {
                    return Result.EXIT;
                }
                p.index++;
                if (!p.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * css用(ブロックコメント、シングルクォート文字列を除外)
     *
     * @param p パーサ
     * @return 結果
     */
    public static Result excludeForStyle(Formatter p) {
        if (p.eat("/*")) {
            p.index = p.indexOf("*/");
            if (p.index < 0) {
                return Result.EXIT;
            }
            p.index += 2;
            return Result.SKIP;
        }
        if (p.eat("'")) {
            for (;;) {
                if (!p.skipUntil('\'')) {
                    return Result.EXIT;
                }
                p.index++;
                if (!p.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * sql用(コメントを除外)
     *
     * @param p パーサ
     * @return 結果(nullの場合は処理終了、trueの場合は次の処理、falseの場合は通常処理)
     */
    public static Result excludeForSql(Formatter p) {
        if (p.eat("--")) {
            p.index = p.indexOf("\n");
            if (p.index < 0) {
                return Result.EXIT;
            }
            p.index++;
            return Result.SKIP;
        }
        if (p.eat("'")) {
            for (;;) {
                if (!p.skipUntil('\'')) {
                    return Result.EXIT;
                }
                p.index++;
                if (!p.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * html用(コメント、シングルクォート文字列を除外)
     *
     * @param p パーサ
     * @return 結果(nullの場合は処理終了、trueの場合は次の処理、falseの場合は通常処理)
     */
    public static Result excludeForHtml(Formatter p) {
        if (p.eat("!--") && !(p.prev("<!--") && p.index < p.lastIndex && p.source.charAt(p.index) == '{')) {
            p.index = p.indexOf("--");
            if (p.index < 0) {
                return Result.EXIT;
            }
            p.index += 2;
            return Result.SKIP;
        }
        if (p.eat("'")) {
            for (;;) {
                if (!p.skipUntil('\'')) {
                    return Result.EXIT;
                }
                p.index++;
                if (!p.prev("\\'")) {
                    break;
                }
            }
        }
        return Result.NEXT;
    }

    /**
     * コンストラクタ
     *
     * @param text ソース文字列
     * @param eval 置換処理
     * @param exclude 除外処理
     */
    Formatter(String text, Function<String, String> eval, Function<Formatter, Result> exclude) {
        source = new StringBuilder(text);
        index = 0;
        lastIndex = source.length();
        braces = new LinkedList<>();
        while (index < lastIndex) {
            skipSpaces();
            if (exclude != null && braces.isEmpty()) {
                Result b = exclude.apply(this);
                if (b == Result.EXIT) {
                    return;
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
                switch (source.charAt(start)) {
                case '<':
                    eat("-->");
                    break;
                case '/':
                    eat("*/");
                    break;
                default:
                    break;
                }
                int end = index;
                String before = source.substring(start, end);
                String after = Tool.string(eval.apply(before)).orElse("");
                source.replace(start, end, after);
                lastIndex = source.length();
                index = end + after.length() - before.length();
                continue;
            }
            index++;
        }
    }

    /**
     * 空白文字を読み飛ばす
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
     * 指定文字まで読み飛ばす
     *
     * @param letter 指定文字
     * @return true:指定文字が見つかった, false:見つからなかった
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
     * 指定文字列を読む
     *
     * @param word 指定文字列
     * @return true:指定文字列を読めた, false:読めなかった
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
     * 直前が指定文字列かどうかを調べる
     *
     * @param word 指定文字列
     * @return true:指定文字列と一致した, false:一致しなかった
     */
    boolean prev(String word) {
        int length = word.length();
        return index - length >= 0 && source.substring(index - length, index).equals(word);
    }

    /**
     * 現在位置以降の指定文字列を位置を取得
     *
     * @param word 指定文字列
     * @return 指定文字列の開始位置, -1:指定文字列が見つからなかった
     */
    int indexOf(String word) {
        return source.indexOf(word, index);
    }

    /**
     * 文字列にパラメータを埋め込み
     *
     * @param exclude 除外処理(戻り値がnullの場合は処理終了、trueの場合は次の処理、falseの場合は通常処理, 通常Parser::excludeFor~を指定)
     * @param escape エスケープ処理(#{}以外に使用, 通常Tool::htmlEscapeを指定)
     * @param text 文字列({key}にメッセージ, ${expression}でEL式, #{expression}でエスケープなしEL式結果を埋め込む)
     * @param map 名前付きパラメータ（${key}にvalueが埋め込まれる）
     * @param values パラメータ（{0}, {1}...に埋め込まれる）
     * @return 埋め込み後の文字列
     */
    public static String format(Function<Formatter, Result> exclude, Function<Object, String> escape, String text, Map<String, Object> map, Object... values) {
        ELProcessor[] el = { null };
        Map<String, String> cache = new HashMap<>();
        return new Formatter(text, expression -> {
            return cache.computeIfAbsent(expression, s -> {
                boolean isEl = !s.startsWith("{");
                boolean[] isEscape = { !isEl || s.charAt(0) == '$' };
                int prefix = isEl ? 2 : 1;
                int suffix = 1;
                if (s.startsWith("{/*") || s.startsWith("/*{")) {
                    isEl = true;
                    prefix = suffix = 3;
                    isEscape[0] = false;
                } else if (s.startsWith("<!--{")) {
                    prefix = 5;
                    suffix = 4;
                    isEscape[0] = false;
                }
                BiFunction<Object, String, String> getResult = (result, type) -> {
                    if (escape != null && isEscape[0]) {
                        String escaped = escape.apply(result);
                        logger.config(() -> "[" + type + "] " + s + " -> " + escaped);
                        return escaped;
                    }
                    String string = Tool.string(result).orElse(null);
                    logger.config(() -> "[raw " + type + "] " + s + " -> " + string);
                    return string;
                };
                String key = s.substring(prefix, s.length() - suffix);
                if (isEl) {
                    if (map != null && map.containsKey(key)) {
                        return getResult.apply(map.get(key), "map");
                    }
                    try {
                        if (el[0] == null) {
                            el[0] = new ELProcessor();
                            // el[0].defineFunction("F", "include", Tool.class.getMethod("include", String.class, Object.class, Object[].class));
                            // el[0].defineFunction("F", "includeIf",
                            // Tool.class.getMethod("includeIf", boolean.class, String.class, Object.class, Object[].class));
                            // el[0].defineFunction("F", "includeN", Tool.class.getMethod("includeN", int.class, String.class, Object.class, Object[].class));
                            // el[0].defineFunction("F", "includeFor",
                            // Tool.class.getMethod("includeFor", Stream.class, String.class, Object.class, Object[].class));
                            // el[0].defineFunction("F", "json", Tool.class.getMethod("json", Object.class, boolean.class));
                            el[0].defineBean("C", Config.properties);
                            el[0].defineBean("M", Message.messages);
                            el[0].defineBean("V", values == null ? new Object[] {} : values);
                            el[0].defineBean("A", Server.application);
                            el[0].defineBean("S", new Session());
                            el[0].defineBean("R", new Request());
                            if (map != null) {
                                map.entrySet().stream().forEach(p -> el[0].defineBean(p.getKey(), p.getValue()));
                            }
                        }
                        return getResult.apply(Tool.string(el[0].eval(key)).orElse(""), "el");
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "el error", e);
                        return s;
                    }
                }
                if (key.matches("^[0-9]+$")) {
                    int i = Integer.parseInt(key);
                    if (values != null && i < values.length) {
                        return getResult.apply(values[i] == null ? null : values[i].toString(), "values");
                    } else {
                        return s;
                    }
                }
                if (key.indexOf('\n') < 0) {
                    String[] keys = key.split("\\s*:\\s*");
                    boolean hasParameter = keys.length > 1;
                    if (hasParameter) {
                        key = keys[0];
                        keys = Arrays.copyOfRange(keys, 1, keys.length);
                    }
                    if (Message.messages.containsKey(key)) {
                        return getResult.apply(hasParameter ? new MessageFormat(Message.get(key)).format(keys) : Message.get(key), "message");
                    }
                }
                return s;
            });
        }, exclude).source.toString();
    }
}
