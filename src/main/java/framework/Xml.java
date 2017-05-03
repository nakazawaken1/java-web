package framework;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * XML builder
 */
public class Xml {

    /**
     * Indent
     */
    public static String indent = "  ";

    /**
     * Newline
     */
    public static String newline = "\r\n";

    /**
     * No-child tag
     */
    public static Set<String> singles = Tool.set("area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source");

    /**
     * Namespace
     */
    public static String namespace = "http://vav.jp/java-web";

    /**
     * Render attribute name
     */
    public static String attribute = "render";

    /**
     * Parser factory
     */
    static SAXParserFactory factory = Tool.peek(SAXParserFactory.newInstance(), f -> f.setNamespaceAware(true));

    /**
     * Tag name (exclude &lt;, &gt;. invisible tag if null or empty)
     */
    protected String tag;

    /**
     * True if text node
     */
    protected boolean isText;

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
     */
    protected Xml() {
    }

    /**
     * @return True if invisible tag
     */
    protected boolean isNull() {
        return tag == null || tag.isEmpty();
    }

    /**
     * @param tag Tag name
     * @return Node
     */
    public static Xml of(String tag) {
        return Tool.peek(new Xml(), xml -> xml.tag = tag);
    }

    /**
     * Constructor for text node
     * 
     * @param text Text
     * @return Text node
     */
    public static Xml textOf(String text) {
        return Tool.peek(new Xml(), xml -> {
            xml.tag = text;
            xml.isText = true;
        });
    }

    /**
     * @param source Source
     * @return Xml
     */
    public static Xml parse(String source) {
        return parse(source, null);
    }

    /**
     * @param source Source
     * @param renders Renders(name: render)
     * @return Xml
     */
    public static Xml parse(String source, Map<String, Function<Xml, Xml>> renders) {
        try {
            Xml result = of(null);
            factory.newSAXParser().parse(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), new DefaultHandler() {

                Xml xml = result;

                /*
                 * (non-Javadoc)
                 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
                 */
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    xml = xml.child(qName);
                    IntStream.range(0, attributes.getLength())
                            .forEach(i -> xml.attr(
                                    namespace.equals(attributes.getURI(i)) ? attributes.getURI(i) + attributes.getLocalName(i) : attributes.getQName(i),
                                    attributes.getValue(i)));
                }

                /*
                 * (non-Javadoc)
                 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
                 */
                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    Xml self = xml;
                    xml = xml.parent;
                    if (self.attributes == null || renders == null) {
                        return;
                    }
                    String attribute = namespace + Xml.attribute;
                    String render = self.attributes.get(attribute);
                    if (render != null) {
                        self.attributes.remove(attribute);
                        int i = xml.children.indexOf(self);
                        Xml x = xml.children.get(i);
                        xml.children.remove(i);
                        xml.children.add(i, Tool.peek(renders.get(render).apply(x), j -> j.parent = xml));
                    }
                }

                /*
                 * (non-Javadoc)
                 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
                 */
                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    xml.text(new String(ch, start, length));
                }

            });
            return result;
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return root().toString("", xml -> Tool.string(xml.tag).map(singles::contains).orElse(false));
    }

    /**
     * @param indent Indent
     * @param isSingle True if single tag
     * @return String
     */
    public String toString(String indent, Function<Xml, Boolean> isSingle) {
        boolean noChild = children == null || children.isEmpty();
        StringBuilder s = new StringBuilder();
        boolean isBeforeText = before().map(i -> i.isText).orElse(false);
        boolean isNull = isNull();
        if (!(parent == null || isText || isNull || parent.isNull() && parent.parent == null || isBeforeText)) {
            s.append(newline);
        }
        if (isText) {
            if (parent != null && parent.isNull() && parent.parent != null && parent.parent.children.size() > 1) {
                s.append(newline).append(indent).append(indent);
            }
            return s.append(tag).toString();
        }
        if (!isNull) {
            if (!isBeforeText) {
                s.append(indent);
            }
            s.append('<').append(tag);
            if (attributes != null) {
                attributes.entrySet().forEach(i -> s.append(' ').append(i.getKey()).append("=\"").append(i.getValue()).append('"'));
            }
            if (noChild && isSingle.apply(this)) {
                return s.append(" />").toString();
            }
            s.append('>');
        }
        if (!noChild) {
            boolean[] isText = { isNull };
            children.forEach(i -> {
                s.append(i.toString(isNull ? indent : indent + Xml.indent, isSingle));
                isText[0] = isText[0] || i.isText;
            });
            if (!isText[0]) {
                s.append(newline).append(indent);
            }
        }
        return isNull ? s.toString() : s.append("</").append(tag).append('>').toString();
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
     * @return Before node
     */
    public Optional<Xml> before() {
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
    public Optional<Xml> after() {
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
     * Add child
     * 
     * @param tag Tag
     * @return Added child
     */
    public Xml child(String tag) {
        if (children == null) {
            children = new ArrayList<>();
        }
        Xml c = of(tag);
        c.parent = this;
        children.add(c);
        return c;
    }

    /**
     * Add children
     * 
     * @param tag Tag
     * @param texts Texts
     * @return Self
     */
    public Xml child(String tag, Stream<Object> texts) {
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
            this.children = flat.peek(i -> i.parent = this).collect(Collectors.toList());
        } else {
            flat.peek(i -> i.parent = this).forEach(this.children::add);
        }
        return this;
    }

    /**
     * @param child Child
     * @return Added child
     */
    public Xml child(Xml child) {
        child.parent = this;
        if (this.children == null) {
            this.children = Tool.peek(new ArrayList<>(), a -> a.add(child));
        } else {
            this.children.add(child);
        }
        return child;
    }

    /**
     * @param tag Tag
     * @return Added sibling
     */
    public Xml sibling(String tag) {
        return parent.child(tag);
    }

    /**
     * @param tag Tag
     * @param texts Texts
     * @return Self
     */
    public Xml sibling(String tag, Stream<Object> texts) {
        parent.child(tag, texts);
        return this;
    }

    /**
     * @param sibling Sibling
     * @return Self
     */
    public Xml sibling(Stream<Xml> sibling) {
        parent.child(sibling);
        return this;
    }

    /**
     * Set text(clear existing children)
     * 
     * @param text Text
     * @return Self
     */
    public Xml text(Object text) {
        Tool.string(text).map(String::trim).filter(Tool.notEmpty).ifPresent(i -> clear().child(i).isText = true);
        return this;
    }

    /**
     * Set attribute
     * 
     * @param name Name
     * @param value Value
     * @return Self
     */
    public Xml attr(String name, String value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, value);
        return this;
    }

    /**
     * Set attributes
     * 
     * @param stream Name-value pairs
     * @return Self
     */
    public Xml attr(Stream<Tuple<String, String>> stream) {
        stream.forEach(i -> attr(i.l, i.r));
        return this;
    }

    /**
     * Clear all children
     * 
     * @return Self
     */
    public Xml clear() {
        if (children != null) {
            children.stream().forEach(i -> i.parent = null);
            children = null;
        }
        return this;
    }

    /**
     * Remove match children
     * 
     * @param filter return true if remove
     * @return Self
     */
    public Xml remove(Predicate<Xml> filter) {
        if (children != null) {
            for (Iterator<Xml> i = children.iterator(); i.hasNext();) {
                Xml xml = i.next();
                if (filter.test(xml)) {
                    i.remove();
                    xml.parent = null;
                }
            }
        }
        return this;
    }

    /**
     * Remove children
     * 
     * @param child Child for remove
     * @return Self
     */
    public Xml remove(Stream<Xml> child) {
        if (children != null) {
            for (Xml c : child.toArray(Xml[]::new)) {
                children.remove(c);
                c.parent = null;
            }
        }
        return this;
    }

    /**
     * @param parent Inner use only
     * @return Copied xml
     */
    public Xml copy(Xml... parent) {
        Xml to = of(tag);
        to.isText = isText;
        if (parent.length > 0) {
            to.parent = parent[0];
        }
        to.children = children == null ? null : children.stream().map(i -> i.copy(to)).collect(Collectors.toList());
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
     * Example
     * 
     * @param args No use
     */
    public static void main(String[] args) {
        Xml xml = Xml.of("b");
        System.out.println(xml.child("i").text("test"));
        System.out.println(xml.copy().clear().child("a").text("test2"));
        System.out.println(Xml.of("br").attr(Stream.of(Tuple.of("style", "clear:both"), Tuple.of("id", "test"))));
        System.out.println(Xml.of("span").text("abc"));
        System.out.println(Xml.of("ol").child("li").text("1").sibling("li").text("2"));
        System.out.println(Xml.of("ol").child("li", Stream.of("1", "2")));
        System.out.println(Xml.of("ol").child(IntStream.rangeClosed(1, 2).mapToObj(i -> Xml.of("li").text(i))));
        System.out.println(Xml.of("table").child("thead").child("tr").child("th", Stream.of("a", "b")).root().child("tbody").child("tr")
                .child("th", Stream.of(1, 2)).sibling("tr").child("th", Stream.of(3, 4)));
        Function<Xml, Xml> a = div -> Xml.of(null).text("あいうえお");
        Function<Xml, Xml> b = ul -> ul.clear().child("li", Stream.of("aa", "bb", "cc"));
        Function<Xml, Xml> c = tr -> tr.repeat(Stream.of(Tuple.of(1, "Tom", 23), Tuple.of(2, "Sam", 41), Tuple.of(3, "Andy", 33)), (t, i) -> {
            t.children().get(0).text(i.l);
            t.children().get(1).text(i.r.l);
            t.children().get(2).text(i.r.r);
            return t;
        });
        System.out.println(parse("<body xmlns:j=\"http://vav.jp/java-web\">" + //
                "<div j:render=\"a\">" + //
                "<!--abcde-->" + //
                "</div>" + //
                "<ul j:render=\"b\">" + //
                "  <li>a</li>" + //
                "  <li>b</li>" + //
                "</ul>" + //
                "<table>" + //
                "  <tr><th>id</th><th>name</th><th>age</th></tr>" + //
                "  <tr j:render=\"c\"><th class=\"number\">1</th><td>Jon</td><td class=\"number\">22</td></tr>" + //
                "</table>" + //
                "</body>", Tool.map("a", a, "b", b, "c", c)));
    }
}
