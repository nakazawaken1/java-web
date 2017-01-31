package framework;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.servlet.http.HttpSession;

/**
 * session scoped object
 */
public abstract class Session implements Attributes<Object> {
    /**
     * current request
     */
    transient static final ThreadLocal<Session> current = new ThreadLocal<>();

    /**
     * session key of account
     */
    public static final String sessionKey = "\naccount\n";

    /**
     * getters
     */
    static final Getters getters = new Getters(Session.class);

    /**
     * For servlet
     */
    public static class ForServlet extends Session {

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#clear()
         */
        @Override
        public void clear() {
            session.invalidate();
        }

        /**
         * http session
         */
        HttpSession session;

        /**
         * constructor
         * @param session session
         */
        ForServlet(HttpSession session) {
            this.session = session;
            session.setMaxInactiveInterval(Config.app_session_timeout_seconds.integer());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#names()
         */
        @Override
        public Stream<String> names() {
            return Tool.stream(session.getAttributeNames());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#getAttr(java.lang.String)
         */
        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> session.getAttribute(name)));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Object value) {
            session.setAttribute(name, value);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#removeAttr(java.lang.String)
         */
        @Override
        public void removeAttr(String name) {
            session.removeAttribute(name);
        }
    }

    /**
     * For server
     */
    public static class ForServer extends Session {
        
        /**
         * session id
         */
        String sessionId;
        
        /**
         * Attributes
         */
        static Map<String, Object> attributes = new LinkedHashMap<>();

        /**
         * @param sessionId session id
         */
        ForServer(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String toString() {
            return sessionId;
        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T)attributes.get(name));
        }

        @Override
        public void setAttr(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttr(String name) {
            attributes.remove(name);
        }
    }
    
    /**
     * @return account
     */
    public Account getAccount() {
        return this.<Account>getAttr(sessionKey).orElse(Account.GUEST);
    }

    /**
     * @return is logged in
     */
    public boolean isLoggedIn() {
        return getAttr(sessionKey).isPresent();
    }

    /**
     * @param loginId login id
     * @param password password
     * @return true if success, else failure
     */
    public boolean login(String loginId, String password) {
        try {
            setAttr(sessionKey, Class.forName(Config.app_account_class.text()).getConstructor(String.class, String.class).newInstance(loginId, password));
            return true;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    /**
     * @return login id
     */
    public String logout() {
        String result = getAccount().getId();
        clear();
        return result;
    }
}
