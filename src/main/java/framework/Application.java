package framework;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.servlet.ServletContext;

/**
 * application scoped object
 */
public interface Application extends Attributes<Object> {

    /**
     * singleton
     */
    Lazy<Application> CURRENT = new Lazy<>(null);

    /**
     * getters
     */
    Getters getters = new Getters(Application.class);

    /**
     * @return singleton
     */
    static Optional<Application> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * For Servlet
     */
    class ForServlet implements Application {
        @Override
        public String toString() {
            return "real path: " + raw.getRealPath("") + ", context path: " + raw.getContextPath();

        }

        /**
         * application scope object
         */
        transient final ServletContext raw;

        /**
         * constructor
         * 
         * @param raw application scope object
         */
        ForServlet(ServletContext raw) {
            this.raw = raw;
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#names()
         */
        @Override
        public Stream<String> names() {
            return Tool.stream(raw.getAttributeNames());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#getAttr(java.lang.String)
         */
        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> raw.getAttribute(name)));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Object value) {
            raw.setAttribute(name, value);
        }

        /*
         * (non-Javadoc)
         * 
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
        @Override
        public String toRealPath(String relativePath) {
            return raw.getRealPath(relativePath);
        }

        /**
         * @return context path
         */
        @Override
        public String getContextPath() {
            return Tool.suffix(raw.getContextPath(), "/");
        }
    }

    /**
     * For Server
     */
    class ForServer implements Application {
        /**
         * Context path
         */
        String contextPath;
        /**
         * Attributes
         */
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        /**
         * @param contextPath context path
         */
        ForServer(String contextPath) {
            this.contextPath = contextPath;
        }

        @Override
        public String toString() {
            return "real path: " + toRealPath("") + ", context path: " + getContextPath();

        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> attributes.get(name)));
        }

        @Override
        public void setAttr(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttr(String name) {
            attributes.remove(name);
        }

        @Override
        public String toRealPath(String relativePath) {
            return Config.toURL(relativePath).toString();
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }
    }

    /**
     * @param relativePath relative path from htdocs
     * @return full path
     */
    String toRealPath(String relativePath);

    /**
     * @return context path
     */
    String getContextPath();
}
