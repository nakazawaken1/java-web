package framework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.servlet.http.HttpSession;

import com.sun.net.httpserver.HttpExchange;

/**
 * session scoped object
 */
@SuppressWarnings("restriction")
public abstract class Session implements Attributes<Serializable> {

    /**
     * current session
     */
    transient static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    /**
     * @return current session
     */
    public static Optional<Session> current() {
        return Optional.ofNullable(CURRENT.get());
    }

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
        public <T extends Serializable> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> session.getAttribute(name)));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Serializable value) {
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
        static final String NAME = Config.app_session_name.text();

        /**
         * session id
         */
        String id;

        /**
         * Attributes
         */
        Map<String, Serializable> attributes;

        /**
         * saved attributes
         */
        boolean saved = false;

        /**
         * @param exchange exchange
         */
        @SuppressWarnings("unchecked")
        ForServer(HttpExchange exchange) {
            id = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie")).map(s -> Stream.of(s.split("\\s*;\\s*")).map(t -> t.split("=", 2))
                    .filter(a -> NAME.equalsIgnoreCase(a[0])).findAny().map(a -> a[1].replaceFirst("\\..*$", "")).orElse(null)).orElse(null);
            if (id == null) {
                id = Tool.hash("" + hashCode() + System.currentTimeMillis() + exchange.getRemoteAddress() + Math.random());
                exchange.getResponseHeaders().add("Set-Cookie",
                        createSetCookie(NAME, id + Config.app_cluster_suffix.text(), null, -1, null, Application.current().map(Application::getContextPath).orElse(null), false, true));
            } else {
                try (Db db = Db.connect()) {
                    int timeout = Config.app_session_timeout_minutes.integer();
                    if (timeout > 0) {
                        db.from("t_session").where("last_access", "<", LocalDateTime.now().minusMinutes(timeout)).delete();
                    }
                    db.select("value").from("t_session").where("id", id).row(rs -> {
                        try (InputStream in = rs.getBinaryStream(1)) {
                            if (in != null) {
                                ObjectInputStream o = new ObjectInputStream(in);
                                attributes = (Map<String, Serializable>) o.readObject();
                            }
                        }
                    });
                }
            }
            if (attributes == null) {
                attributes = new ConcurrentHashMap<>();
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
        public <T extends Serializable> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> attributes.get(name)));
        }

        @Override
        public void setAttr(String name, Serializable value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttr(String name) {
            attributes.remove(name);
        }

        /**
         * save session attributes
         */
        public void save() {
            if (saved) {
                return;
            }
            try (Db db = Db.connect(); ByteArrayOutputStream out = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(out)) {
                o.writeObject(attributes);
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                if (db.preparedQuery("SELECT * FROM t_session WHERE id = ? FOR UPDATE", ps -> {
                    ps.setString(1, id);
                    return Tool.array(id);
                }, rs -> db.preparedExecute("UPDATE t_session SET value = ?, last_access = ? WHERE id = ?", ps -> {
                    ps.setBytes(1, out.toByteArray());
                    ps.setTimestamp(2, now);
                    ps.setString(3, id);
                    return Tool.array("(blob)", now, id);
                })) <= 0) {
                    db.preparedExecute("INSERT INTO t_session(id, value, last_access) VALUES(?, ?, ?)", ps -> {
                        ps.setString(1, id);
                        ps.setBytes(2, out.toByteArray());
                        ps.setTimestamp(3, now);
                        return Tool.array(id, "(blob)", now);
                    });
                }
            } catch (IOException e) {
                Logger.getLogger(Session.class.getName()).log(Level.SEVERE, null, e);
            }
        }

        @Override
        public void close() throws Exception {
            save();
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
        Optional<Account> a = Try
                .s(() -> Tool.<Optional<Account>>invoke(Config.app_login_method.text(), Tool.array(String.class, String.class), loginId, password), e -> {
                    Tool.getLogger().warning(Tool.print(e::printStackTrace));
                    return Optional.<Account>empty();
                }).get();
        return Tool.ifPresentOr(a, i -> {
            setAttr(sessionKey, i);
            Tool.getLogger().info("logged in : " + loginId);
        }, () -> Tool.getLogger().info("login failed: " + loginId));
    }

    /**
     * @return login id
     */
    public String logout() {
        String result = getAccount().id;
        clear();
        return result;
    }
}
