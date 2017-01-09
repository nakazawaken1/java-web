package framework;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * XML builder
 */
public class Xml {
    
    /**
     * indent
     */
    public static final String indent = "  ";
    
    /**
     * newline
     */
    public static final String newline = "\r\n";

    /**
     * tag name (exclude <, >)
     */
    protected String tag;
    
    /**
     * parent node
     */
    protected Xml parent;
    
    /**
     * child node
     */
    protected List<Xml> children;
    
    /**
     * text node
     */
    protected Object text;
    
    /**
     * attributes
     */
    protected Map<String, Object> attributes;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return root().toStringLower("");
    }
    
    /**
     * @return root node
     */
    public Xml root() {
        Xml root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        return root;
    }
    
    /**
     * @return parent node
     */
    public Xml parent() {
        return parent;
    }

    /**
     * @param indent indent
     * @return string
     */
    public String toStringLower(String indent) {
        boolean noChild = children == null || children.isEmpty();
        boolean noText = text == null;
        String t = noText ? null : text.toString();
        if (t == null || t.isEmpty()) {
            noText = true;
        }
        StringBuilder s = new StringBuilder();
        if(parent != null) {
            s.append(newline);
        }
        s.append(indent).append('<').append(tag);
        if (attributes != null) {
            attributes.entrySet().forEach(i -> s.append(' ').append(i.getKey()).append("=\"").append(i.getValue()).append('"'));
        }
        if (noChild && noText) {
            return s.append(" />").toString();
        }
        s.append('>');
        if (!noChild) {
            children.forEach(i -> s.append(i.toStringLower(indent + Xml.indent)));
        }
        if (!noText) {
            s.append(t);
        }
        if (!noChild) {
            s.append(newline).append(indent);
        }
        return s.append("</").append(tag).append('>').toString();
    }

    /**
     * constructor
     * 
     * @param tag tag name
     * @param text text
     */
    public Xml(String tag, Object text) {
        this.tag = tag;
        this.text = text;
    }

    /**
     * constructor
     * 
     * @param tag tag name
     */
    public Xml(String tag) {
        this.tag = tag;
    }

    /**
     * add child
     * 
     * @param tag tag
     * @param text text
     * @return child
     */
    public Xml child(String tag, Object text) {
        if (children == null) {
            children = new ArrayList<>();
        }
        Xml c = new Xml(tag, text);
        c.parent = this;
        children.add(c);
        return c;
    }

    /**
     * add child
     * 
     * @param tag tag
     * @return added object
     */
    public Xml child(String tag) {
        return child(tag, "");
    }

    /**
     * add child
     * 
     * @param tag tag
     * @param texts text stream
     * @return added object
     */
    public Xml child(String tag, Stream<Object> texts) {
        texts.forEach(text -> child(tag, text));
        return children.get(children.size() - 1);
    }

    /**
     * add child
     * 
     * @param children child stream
     * @return added object
     */
    public Xml child(Stream<Xml> children) {
        if (this.children == null) {
            this.children = children.peek(i -> i.parent = this).collect(Collectors.toList());
        } else {
            children.peek(i -> i.parent = this).forEach(this.children::add);
        }
        return this.children.get(this.children.size() - 1);
    }

    /**
     * @param tag tag
     * @param text text
     * @return added object
     */
    public Xml sibling(String tag, Object text) {
        return parent.child(tag, text);
    }

    /**
     * @param tag tag
     * @return added object
     */
    public Xml sibling(String tag) {
        return parent.child(tag);
    }

    /**
     * @param tag tag
     * @param texts text stream
     * @return added object
     */
    public Xml sibling(String tag, Stream<Object> texts) {
        return parent.child(tag, texts);
    }

    /**
     * @param sibling sibling
     * @return added object
     */
    public Xml sibling(Stream<Xml> sibling) {
        return parent.child(sibling);
    }

    /**
     * set text
     * 
     * @param text text
     * @return self
     */
    public Xml text(Object text) {
        this.text = text;
        return this;
    }

    /**
     * set attribute
     * 
     * @param name name
     * @param value value
     * @return self
     */
    public Xml attr(String name, String value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, value);
        return this;
    }

    /**
     * set attributes
     * 
     * @param stream name-value pair
     * @return self
     */
    public Xml attr(Stream<Map.Entry<String, String>> stream) {
        stream.forEach(i -> attr(i.getKey(), i.getValue()));
        return this;
    }

    /**
     * example
     * 
     * @param args no use
     */
    @SuppressWarnings("serial")
    public static void main(String[] args) {
        System.out.println(new Xml("br").attr("style", "clear:both").attr("id", "test"));
        System.out.println(new Xml("br").attr(new LinkedHashMap<String, String>() {
            {
                put("style", "clear:both");
                put("id", "test");
            }
        }.entrySet().stream()));
        System.out.println(new Xml("span").text("abc"));
        System.out.println(new Xml("span", "abc"));
        System.out.println(new Xml("ol").child("li").text("1").sibling("li").text("2"));
        System.out.println(new Xml("ol").child("li", "1").sibling("li", "2"));
        System.out.println(new Xml("ol").child("li", Stream.of("1", "2")));
        System.out.println(new Xml("ol").child(IntStream.rangeClosed(1, 2).mapToObj(i -> new Xml("li", i))));
        System.out.println(new Xml("table").child("thead").child("tr").child("th", Stream.of("a", "b")).root().child("tbody").child("tr").child("th", Stream.of(1, 2)).parent().sibling("tr").child("th", Stream.of(3, 4)));
    }
}
