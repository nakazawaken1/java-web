package framework;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.servlet.http.HttpSession;

import com.sun.net.httpserver.HttpExchange;

/**
 * session scoped object
 */
@SuppressWarnings("restriction")
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

        /**
         * http session
         */
        HttpSession session;

        /**
         * constructor
         *
         * @param session session
         */
        ForServlet(HttpSession session) {
            this.session = session;
            session.setMaxInactiveInterval(Config.app_session_timeout_minutes.integer() * 60);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#clear()
         */
        @Override
        public void clear() {
            session.invalidate();
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
    public static class ForServer extends Session implements AutoCloseable {

        /**
         * session cookie name
         */
        static final String name = Config.app_session_name.text();
        
        /**
         * session id
         */
        String id;

        /**
         * closer
         */
        BiConsumer<Db, byte[]> closer;

        /**
         * Attributes
         */
        Map<String, Object> attributes;

        /**
         * @param exchange exchange
         */
        @SuppressWarnings("unchecked")
        ForServer(HttpExchange exchange) {
            id = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie")).map(s -> Stream.of(s.split("\\s*;\\s*")).map(t -> t.split("=", 2))
                    .filter(a -> name.equalsIgnoreCase(a[0])).findAny().map(a -> a[1]).orElse(null)).orElse(null);
            if (id != null) {
                try (Db db = Db.connect()) {
                    int timeout = Config.app_session_timeout_minutes.integer();
                    if(timeout > 0) {
                        db.from("t_session").where("last_access", "<", LocalDateTime.now().minusMinutes(timeout)).delete();
                    }
                    db.select("value").from("t_session").where("id", id).row(rs -> {
                        try (InputStream in = rs.getBinaryStream(1)) {
                            if(in != null) {
                                ObjectInputStream o = new ObjectInputStream(in);
                                attributes = (Map<String, Object>) o.readObject();
                            } else {
                                attributes = new LinkedHashMap<>();
                            }
                        }
                    });
                }
            } else {
                id = Tool.digest((id + exchange.getRemoteAddress() + Math.random()).getBytes(StandardCharsets.UTF_8), "SHA-256");
                exchange.getResponseHeaders().add("Set-Cookie", createSetCookie(name, id, null, -1, null, Application.current.get().getContextPath(), false, true));
            }
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            if (attributes == null) {
                attributes = new LinkedHashMap<>();
                closer = (db, in) -> {
                    db.prepare("INSERT INTO t_session(id, value, last_access) VALUES(?, ?, ?)", ps -> {
                        ps.setString(1, id);
                        ps.setBytes(2, in);
                        ps.setTimestamp(3, now);
                        return Tool.array(id, "(blob)", now);
                    });
                };
            } else {
                closer = (db, in) -> {
                    db.prepare("UPDATE t_session SET value = ?, last_access = ? WHERE id = ?", ps -> {
                        ps.setBytes(1, in);
                        ps.setTimestamp(2, now);
                        ps.setString(3, id);
                        return Tool.array("(blob)", now, id);
                    });
                };
            }
        }

        /**
         * @param name cookie name
         * @param value cookie value
         * @param expires expires date time
         * @param maxAge max age seconds(enabled if positive)
         * @param domain scope suffix
         * @param path scope path
         * @param secure allow only HTTPS
         * @param httpOnly reject script access
         * @return set-cookie value(without Set-Cookie:)
         */
        static String createSetCookie(String name, String value, ZonedDateTime expires, long maxAge, String domain, String path, boolean secure,
                boolean httpOnly) {
            StringBuilder result = new StringBuilder(name + "=" + value);
            Optional.ofNullable(expires).map(DateTimeFormatter.RFC_1123_DATE_TIME::format).ifPresent(s -> result.append("; Expires=").append(s));
            if (maxAge > 0) {
                result.append("; Max-Age=").append(maxAge);
            }
            Optional.ofNullable(domain).ifPresent(s -> result.append("; Domain=").append(s));
            Optional.ofNullable(path).ifPresent(s -> result.append("; Path=").append(s));
            if (secure) {
                result.append("; Secure");
            }
            if (httpOnly) {
                result.append("; HttpOnly");
            }
            return result.toString();
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) attributes.get(name));
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
        public void close() throws Exception {
            try (Db db = Db.connect(); ByteArrayOutputStream out = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(out)) {
                o.writeObject(attributes);
                closer.accept(db, out.toByteArray());
            } catch (IOException e) {
                Logger.getLogger(Session.class.getName()).log(Level.SEVERE, null, e);
            }
        }
    }

    /**
     * @return account
     */
    public Account getAccount() {
        return this.<Account> getAttr(sessionKey).orElse(Account.GUEST);
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
