package framework;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * XML builder
 */
public class Xml {

    /**
     * Indent
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static String indent = "  ";

    /**
     * Newline
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static String newline = "\r\n";

    /**
     * No-child tag
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static Set<String> singles = Tool.set("area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source");

    /**
     * Render attribute name
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static String attribute = "data-render";

    /**
     * True if reverse comments
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static boolean isReserveComment = true;

    /**
     * Tag name(exclude &lt;, &gt;. invisible tag if null or empty) or text
     */
    public final String content;

    /**
     * Node type
     */
    enum Type {
        /**
         * tag
         */
        tag,
        /**
         * escaped text
         */
        text,
        /**
         * unescaped text
         */
        html,
        /**
         * comment
         */
        comment,
        /**
         * control
         */
        control;
        /**
         * @return True if must to escape
         */
        public boolean isEscape() {
            return this == text;
        }

        /**
         * @return True if text node
         */
        public boolean isText() {
            return this != tag;
        }
    }

    /**
     * True if text node
     */
    public final Type type;

    /**
     * Parent node
     */
    protected Xml parent;

    /**
     * Child node
     */
    protected List<Xml> children;

    /**
     * Attributes
     */
    protected Map<String, String> attributes;

    /**
     * Hidden
     * 
     * @param content Content
     * @param type Node type
     */
    public Xml(Object content, Type type) {
        this.content = content == null ? null : String.valueOf(content);
        this.type = type;
    }

    /**
     * @param tag Tag name
     * @return Node
     */
    public static Xml of(String tag) {
        return new Xml(tag, Type.tag);
    }

    /**
     * @return True if invisible tag
     */
    protected boolean isNull() {
        return content == null || content.isEmpty();
    }

    /**
     * Simple API for (X)HTML
     */
    static class Sax extends AbstractParser {

        /**
         * Event handler
         */
        interface Handler {
            /**
             * logger
             */
            static final Logger logger = Logger.getLogger(Handler.class.getCanonicalName());

            /**
             * @param tag Tag name
             */
            default void tagStart(CharSequence tag) {
                logger.info("tagStart: " + tag);
            }

            /**
             * @param tag Tag name
             * @param name Attribute name
             * @param value Attribute value
             */
            default void attribute(CharSequence tag, CharSequence name, CharSequence value) {
                logger.info("attribute: " + name + " = " + value + " @ " + tag);
            }

            /**
             * @param tag Tag name
             * @param control Control
             */
            default void control(CharSequence tag, CharSequence control) {
                logger.info("control: " + control + " @ " + tag);
            }

            /**
             * @param tag Tag name
             * @param comment Comment
             */
            default void comment(CharSequence tag, CharSequence comment) {
                logger.info("comment: " + comment + " @ " + tag);
            }

            /**
             * @param tag Tag name
             * @param text Text
             */
            default void text(CharSequence tag, CharSequence text) {
                logger.info("text: " + text + " @ " + tag);
            }

            /**
             * @param tag Tag name
             */
            default void tagEnd(CharSequence tag) {
                logger.info("tagEnd: " + tag);
            }
        }

        /**
         * Tags that can omit the close tag(optional tag: next tags)
         */
        @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
        public static Map<String, Set<String>> optionalTags = Tool.map("li", Tool.set("li"), "dd", Tool.set("dt", "dd"), "dt", Tool.set("dt", "dd"), "p", Tool
            .set("address", "article", "aside", "blockquote", "details", "div", "dl", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "main", "menu", "nav", "ol", "p", "pre", "section", "table", "ul"), "rp", Tool
                .set("rp", "rt"), "rt", Tool.set("rp", "rt"), "optgroup", Tool.set("optgroup"), "option", Tool
                    .set("optgroup", "option"), "thead", Tool.set("tbody", "tfoot"), "tbody", Tool.set("thead", "tfoot"), "tr", Tool
                        .set("tr"), "td", Tool.set("td", "th", "tr"), "th", Tool.set("td", "th", "tr"), "colgroup", Tool
                            .set("thead", "tbody", "tfoot", "colgroup", "tr"), "caption", Tool.set("thead", "tbody", "tfoot", "colgroup", "tr"));

        /**
         * Non-parse tags
         */
        @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
        public static Set<String> nonParseTags = Tool.set("script", "style");

        /**
         * Tag stack
         */
        Deque<String> stack = new LinkedList<>();

        /**
         * @return Current tag
         */
        String peek() {
            return stack.isEmpty() ? null : stack.peek();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.AbstractParser#set(java.lang.String)
         */
        @Override
        void set(String text) {
            stack.clear();
            super.set(text);
        }

        /**
         * @param text target
         * @param handler Handler
         */
        public void parse(String text, Handler handler) {
            set(text);
            while (index < lastIndex) {
                skipSpaces();
                if (eat("<")) {
                    if (eat("/")) { /* end tag */
                        int start = index;
                        if (skipUntil('>')) {
                            int end = index;
                            eat(">");
                            String name = subSequence(start, end).toString();
                            while (!stack.isEmpty()) {
                                String s = stack.pop();
                                handler.tagEnd(s);
                                if (name.equalsIgnoreCase(s)) {
                                    break;
                                }
                            }
                        }
                        continue;
                    }
                    if (eat("!") || eat("?")) {
                        if (eat("--")) {/* comment */
                            int start = index;
                            int end = indexOf("-->");
                            handler.comment(peek(), subSequence(start, end));
                            index = end + "-->".length();
                            continue;
                        }
                        /* Document type definition or XML Declaration */
                        int start = index - "<!".length();
                        skipUntil('>');
                        eat(">");
                        int end = index;
                        handler.control(peek(), subSequence(start, trimRight(end)));
                        continue;
                    }
                    int start = index;
                    if (skipUntil('\t', '\r', '\n', ' ', '/', '>')) { /* start tag */
                        int end = index;
                        String name = subSequence(start, end).toString();
                        String lower = name.toLowerCase(Locale.ENGLISH);
                        /* optional tag check */
                        while (!stack.isEmpty()) {
                            Set<String> set = optionalTags.get(stack.peek());
                            if (set != null && set.contains(lower)) {
                                handler.tagEnd(stack.pop());
                            } else {
                                break;
                            }
                        }
                        handler.tagStart(name);
                        skipSpaces();
                        if (eat("/")) { /* single tag */
                            skipSpaces();
                            eat(">");
                            handler.tagEnd(name);
                            continue;
                        }
                        stack.push(name);
                        while (!(eat(">") || eat("/>"))) { /* attributes */
                            start = index;
                            end = indexOf("=");
                            index = end + 1;
                            skipSpaces();
                            int valueStart;
                            int valueEnd = -1;
                            if (eat("\"")) {
                                valueStart = index;
                                if (skipUntil('"')) {
                                    valueEnd = index;
                                    eat("\"");
                                }
                            } else {
                                valueStart = index;
                                if (skipUntil((spaces + "/>").toCharArray())) {
                                    valueEnd = index;
                                }
                            }
                            if (valueEnd >= 0) {
                                handler.attribute(name, subSequence(start, end), subSequence(valueStart, valueEnd));
                                skipSpaces();
                            }
                        }
                        if (singles.contains(lower)) {
                            skipSpaces();
                            eat("/");/* optional */
                            eat(">");
                            handler.tagEnd(name);
                            stack.pop();
                            continue;
                        }
                        if (nonParseTags.contains(lower)) { /* non-parse tag */
                            start = index;
                            String tag = '/' + name + '>';
                            do {
                                skipUntil('<');
                                eat("<");
                            } while (index < lastIndex && !tag.equals(subSequence(index, index + tag.length())));
                            end = index - 1;
                            handler.text(name, subSequence(start, end));
                            handler.tagEnd(name);
                            stack.pop();
                            index += tag.length();
                        }
                    }
                    continue;
                }
                /* text */
                int start = index;
                skipUntil('<');
                if (start < index) {
                    int end = index;
                    handler.text(peek(), subSequence(start, trimRight(end)));
                }
            }
        }
    }

    /**
     * @param source Source
     * @param renders Renders
     * @return Xml
     */
    @SafeVarargs
    public static Xml parse(String source, Function<Xml, Xml>... renders) {
        Map<String, Function<Xml, Xml>> s = renders.length <= 0 ? null
                : Stream.of(renders)
                    .collect(LinkedHashMap::new, (m, r) -> m.put(String.valueOf(m.size()), r), Map::putAll);
        return parseMap(source, s);
    }

    /**
     * @param source Source
     * @param renders Renders(name: render)
     * @return Xml
     */
    public static Xml parseMap(String source, Map<String, Function<Xml, Xml>> renders) {
        Xml result = of(null);
        new Sax().parse(source, new Sax.Handler() {

            Xml xml = result;

            @Override
            public void tagStart(CharSequence tag) {
                xml = xml.child(tag.toString());
            }

            @Override
            public void attribute(CharSequence tag, CharSequence name, CharSequence value) {
                String v = value.toString();
                xml.attr(name.toString(), v);
            }

            @Override
            public void comment(CharSequence tag, CharSequence comment) {
                if (isReserveComment) {
                    xml.child(new Xml(comment, Type.comment));
                }
            }

            @Override
            public void control(CharSequence tag, CharSequence control) {
                xml.child(new Xml(control, Type.control));
            }

            @Override
            public void text(CharSequence tag, CharSequence text) {
                xml.child(new Xml(text, Type.html));
            }

            @Override
            public void tagEnd(CharSequence tag) {
                Xml self = xml;
                xml = xml.parent;
                if (self.attributes == null || renders == null) {
                    return;
                }
                String render = self.attributes.get(attribute);
                if (render != null) {
                    Function<Xml, Xml> r = renders.get(render);
                    if (r == null) {
                        r = Function.identity();
                    }
                    self.attributes.remove(attribute);
                    int i = xml.children.indexOf(self);
                    Xml x = xml.children.get(i);
                    xml.children.remove(i);
                    xml.children.add(i, Tool.peek(r.apply(x), j -> j.parent = xml));
                }
            }
        });
        return result;
    }

    /**
     * @param url Url
     * @return Text
     */
    public static Xml get(String url) {
        try (InputStream in = Tool.peek((HttpURLConnection) new URL(url).openConnection(), Try.c(c -> {
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept-Language", "ja");
        }))
            .getInputStream()) {
            return parse(Tool.loadText(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return root().toString("", xml -> Tool.string(xml.content)
            .map(String::toLowerCase)
            .map(singles::contains)
            .orElse(false))
            .trim();
    }

    /**
     * @param indent Indent
     * @param isSingle True if single tag
     * @return String
     */
    public String toString(String indent, Function<Xml, Boolean> isSingle) {
        StringBuilder s = new StringBuilder();
        if (type.isText()) {
            if (parent != null && parent.isNull()) {
                s.append(newline)
                    .append(indent);
            }
            if (type == Type.comment) {
                return s.append("<!--")
                    .append(content)
                    .append("-->")
                    .toString();
            }
            return s.append(type.isEscape() ? Tool.htmlEscape(content) : content)
                .toString();
        }
        boolean isNull = isNull();
        boolean single = isSingle.apply(this);
        if (!isNull) {
            if (!single) {
                s.append(newline)
                    .append(indent);
            }
            s.append('<')
                .append(content);
            if (attributes != null && !attributes.isEmpty()) {
                attributes.entrySet()
                    .forEach(i -> s.append(' ')
                        .append(i.getKey())
                        .append("=\"")
                        .append(i.getValue())
                        .append('"'));
            }
        }
        boolean noChild = children == null || children.isEmpty();
        if (!isNull) {
            if (noChild && single) {
                return s.append(" />")
                    .toString();
            }
            s.append('>');
        }
        boolean isNewline = false;
        if (!noChild) {
            for (Xml child : children) {
                s.append(child.toString(isNull ? indent : indent + Xml.indent, isSingle));
                if (!child.type.isText()) {
                    isNewline = true;
                }
            }
        }
        if (isNewline && !isNull) {
            s.append(newline)
                .append(indent);
        }
        return isNull ? s.toString()
                : s.append("</")
                    .append(content)
                    .append('>')
                    .toString();
    }

    /**
     * @return Root node
     */
    public Xml root() {
        Xml root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        return root;
    }

    /**
     * @return Parent node
     */
    public Optional<Xml> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * @return Before node
     */
    public Optional<Xml> prev() {
        if (parent != null) {
            int i = parent.children.indexOf(this);
            if (i > 0) {
                return Optional.of(parent.children.get(i - 1));
            }
        }
        return Optional.empty();
    }

    /**
     * @return After node
     */
    public Optional<Xml> next() {
        if (parent != null) {
            int i = parent.children.indexOf(this);
            int size = parent.children.size();
            if (i >= 0 && i < size - 1) {
                return Optional.of(parent.children.get(i + 1));
            }
        }
        return Optional.empty();
    }

    /**
     * @return Non-null Children
     */
    public List<Xml> children() {
        return children == null ? Collections.emptyList() : children;
    }

    /**
     * @return Non-null Attributes
     */
    public Map<String, String> attributes() {
        return attributes == null ? Collections.emptyMap() : attributes;
    }

    /**
     * Add children
     * 
     * @param <T> Element type
     * 
     * @param tag Tag
     * @param texts Texts
     * @return Self
     */
    public <T> Xml child(String tag, Stream<T> texts) {
        texts.forEach(text -> child(tag).text(text));
        return this;
    }

    /**
     * Add children
     * 
     * @param children Children
     * @return Self
     */
    public Xml child(Stream<Xml> children) {
        Stream<Xml> flat = children.flatMap(i -> i.isNull() ? i.children.stream() : Stream.of(i));
        if (this.children == null) {
            this.children = flat.peek(i -> i.parent = this)
                .collect(Collectors.toList());
        } else {
            flat.peek(i -> i.parent = this)
                .forEach(this.children::add);
        }
        return this;
    }

    /**
     * @param child Child
     * @param index index(0: first, -1: last)
     * @return Added
     */
    public Xml child(Xml child, int index) {
        child.parent = this;
        if (children == null) {
            children = Tool.peek(new ArrayList<>(), a -> a.add(child));
        } else if (index == -1) {
            children.add(child);
        } else {
            children.add(index < 0 ? children.size() + index : index, child);
        }
        return child;
    }

    /**
     * @param child Child
     * @return Added
     */
    public Xml child(Xml child) {
        return child(child, -1);
    }

    /**
     * @param tag Tag name
     * @return Added
     */
    public Xml child(String tag) {
        return child(of(tag));
    }

    /**
     * @param xml Xml
     * @return Added
     */
    public Xml after(Xml xml) {
        if (parent == null) {
            return of(null).child(this).parent.child(xml);
        }
        return parent.child(xml, parent.children.indexOf(this) + 1);
    }

    /**
     * @param tag Tag name
     * @return Added
     */
    public Xml after(String tag) {
        return after(of(tag));
    }

    /**
     * @param xml Xml
     * @return Added
     */
    public Xml before(Xml xml) {
        if (parent == null) {
            return of(null).child(this).parent.child(xml, 0);
        }
        return parent.child(xml, parent.children.indexOf(this));
    }

    /**
     * @param tag Tag name
     * @return Added
     */
    public Xml before(String tag) {
        return before(of(tag));
    }

    /**
     * Set text(clear existing children)
     * 
     * @param text Text
     * @return Self
     */
    public Xml text(Object text) {
        clear();
        Tool.string(text)
            .filter(Tool.notEmpty)
            .ifPresent(i -> child(new Xml(text, Type.text)));
        return this;
    }

    /**
     * Set text(clear existing children)
     * 
     * @param text Text
     * @return Self
     */
    public Xml innerHtml(Object text) {
        clear();
        Tool.string(text)
            .filter(Tool.notEmpty)
            .ifPresent(i -> child(new Xml(text, Type.html)));
        return this;
    }

    /**
     * @return Inner HTML
     */
    public String innerHtml() {
        return children().stream()
            .map(child -> child.toString("", xml -> Tool.string(xml.content)
                .map(String::toLowerCase)
                .map(singles::contains)
                .orElse(false))
                .trim())
            .collect(Collectors.joining(newline));
    }

    /**
     * @return Outer HTML
     */
    public String outerHtml() {
        return toString("", xml -> Tool.string(xml.content)
            .map(String::toLowerCase)
            .map(singles::contains)
            .orElse(false)).trim();
    }

    /**
     * @return Inner text
     */
    public String text() {
        return stream().filter(xml -> xml.type == Type.text || xml.type == Type.html)
            .map(xml -> xml.type == Type.text ? xml.content : xml.content.replaceAll("</?[^>]+>", ""))
            .collect(Collectors.joining(" "));
    }

    /**
     * Set attribute
     * 
     * @param name Name
     * @param value Value
     * @return Self
     */
    public Xml attr(String name, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, value == null ? "" : String.valueOf(value));
        return this;
    }

    /**
     * Add attribute
     * 
     * @param name Name
     * @param value Value
     * @param separator Value separator(" " if class)
     * @return Self
     */
    public Xml attr(String name, Object value, String separator) {
        Object v;
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
            v = null;
        } else {
            v = attributes.get(name);
        }
        if (v == null) {
            attributes.put(name, value == null ? "" : String.valueOf(value));
        } else {
            attributes.put(name, v + separator + (value == null ? "" : value));
        }
        return this;
    }

    /**
     * Set attributes
     * 
     * @param map Name-value pairs
     * @return Self
     */
    public Xml attr(Map<String, Object> map) {
        map.forEach(this::attr);
        return this;
    }

    /**
     * Clear all children
     * 
     * @return Self
     */
    public Xml clear() {
        if (children != null) {
            children.stream()
                .forEach(i -> i.parent = null);
            children = null;
        }
        return this;
    }

    /**
     * @param notUse Inner use only
     * @return Copied xml
     */
    public Xml copy(Xml... notUse) {
        Xml to = new Xml(content, type);
        if (notUse.length > 0) {
            to.parent = notUse[0];
        }
        to.children = children == null ? null
                : children.stream()
                    .map(i -> i.copy(to))
                    .collect(Collectors.toList());
        to.attributes = attributes == null ? null : new LinkedHashMap<>(attributes);
        return to;
    }

    /**
     * @param <T> Source type
     * @param source Source
     * @param editor Editor
     * @return Xml
     */
    public <T> Xml repeat(Stream<T> source, BiFunction<Xml, T, Xml> editor) {
        return of(null).child(source.map(j -> editor.apply(copy(), j)));
    }

    /**
     * @return Traversal stream
     */
    public Stream<Xml> stream() {
        Stream.Builder<Xml> builder = Stream.builder();
        streamInner(builder);
        return builder.build();
    }

    /**
     * @param attr Attribute name
     * @param matcher Matcher of attribute value
     * @return Matches nodes
     */
    public Stream<Xml> findBy(String attr, Predicate<String> matcher) {
        return stream().filter(xml -> xml.attributes()
            .containsKey(attr))
            .filter(xml -> matcher.test(xml.attributes.get(attr)));
    }

    /**
     * @param builder builder
     */
    private void streamInner(Stream.Builder<Xml> builder) {
        builder.add(this);
        for (Xml child : children()) {
            child.streamInner(builder);
        }
    }

    /**
     * Example
     * 
     * @param args No use
     */
    public static void main(String[] args) {
        System.out.println(parse("<td>あ<br/>い<br/>う<br/>え<br/>お</td>"));
        // Xml xml = Xml.of("b");
        // System.out.println(xml.child("i").text("test"));
        // System.out.println(xml.copy().clear().child("a").text("test2"));
        // System.out.println(Xml.of("br").attr(Tool.map("style", "clear:both", "id", "test")));
        // System.out.println(Xml.of("span").text("abc"));
        // System.out.println(Xml.of("ol").child("li").text("1").after("li").text("2"));
        // System.out.println(Xml.of("ol").child("li", Stream.of(1, 2)));
        // System.out.println(Xml.of("ol").child(IntStream.rangeClosed(1, 2).mapToObj(i -> Xml.of("li").text(i))));
        // System.out.println(Xml.of("table").child("thead").child("tr").child("th", Stream.of("a", "b")).root().child("tbody").child("tr")
        // .child("th", Stream.of(1, 2)).after("tr").child("th", Stream.of(3, 4)));
        /*
         * Function<Xml, Xml> a = div -> Xml.of(null) .text("あい<a>うえお"); Function<Xml, Xml> b = ul -> ul.clear() .child("li", Stream.of("aa", "bb", "cc"));
         * Function<Xml, Xml> c = tr -> tr.repeat(Stream.of(Tuple.of(1, "Tom", 44), Tuple.of(2, "S<a>m", 55), Tuple.of(3, "Andy", 66)), (t, i) -> { t.children()
         * .get(0) .text(i.l); t.children() .get(1) .text(i.r.l); t.children() .get(2) .text(i.r.r); return t; });
         * System.out.println(parse("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + // "<!DOCTYPE html>\n" + //
         * "<style type=\"text/css\">\nbody > div{\n  font-size:11pt;\n}\n</style>" + //
         * "<script type=\"text/javascript\">\nwindow.onload=function(){\n  if(Math.random() < 0.5) alert('1');\n}\n</script>" + // "<!--abcde-->" + // "<body>"
         * + // "<div data-render=\"0\">" + // "<!--abcde-->" + // "</div>" + // "<ul data-render=\"1\">" + // "  <li>a</li>" + // "  <li>b</li>" + // "</ul>" +
         * // "<table>" + // "  <tr><th>id</th><th>name</th><th>age</th></tr>" + //
         * "  <tr data-render=\"2\"><th class=\"number\">1</th><td>Jon</td><td class=\"number\">22</td></tr>" + // "</table>" + // "</body>", a, b, c)
         * .stream().filter(xml -> "number".equals(xml.attributes().get("class"))).map(Xml::text) .collect(Collectors.joining(System.lineSeparator())) );
         */ // System.out.println(Xml.get("http://www.htmq.com/html5/colgroup.shtml"));
    }
}
