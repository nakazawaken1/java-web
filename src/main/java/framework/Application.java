package framework;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.servlet.ServletContext;

/**
 * application scoped object
 */
public class Application implements Attributes<Object> {
    
    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Request.class.getCanonicalName());

    /**
     * application scope object
     */
    transient final ServletContext raw;
    
    /**
     * constructor
     * @param raw application scope object
     */
    Application(ServletContext raw) {
        logger.info("real path: " + raw.getRealPath(""));
        logger.info("context path: " + raw.getContextPath());
        this.raw = raw;
    }
    
    /* (non-Javadoc)
     * @see framework.Attributes#names()
     */
    @Override
    public Stream<String> names() {
        return Tool.stream(raw.getAttributeNames());
    }

    /* (non-Javadoc)
     * @see framework.Attributes#getAttr(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getAttr(String name) {
        return Optional.ofNullable((T)raw.getAttribute(name));
    }

    /* (non-Javadoc)
     * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
     */
    @Override
    public void setAttr(String name, Object value) {
        raw.setAttribute(name, value);
    }

    /* (non-Javadoc)
     * @see framework.Attributes#removeAttr(java.lang.String)
     */
    @Override
    public void removeAttr(String name) {
        raw.removeAttribute(name);
    }

    /**
     * @param relativePath relative path from htdocs
     * @return full path
     */
    public String toRealPath(String relativePath) {
        return raw.getRealPath(relativePath);
    }

    /**
     * @return context path
     */
    public String contextPath() {
        return raw.getContextPath();
    }
}
