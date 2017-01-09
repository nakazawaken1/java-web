package framework;

import java.util.Optional;
import java.util.stream.Stream;

import javax.servlet.http.HttpSession;

import framework.annotation.Only.Administrator;

/**
 * session scoped object
 */
public class Session implements Attributes<Object> {

    /**
     * current session
     */
    transient final HttpSession raw;
    Session() {
        raw = Request.request.get().getSession();
        raw.setMaxInactiveInterval(Config.app_session_timeout_seconds.integer());
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
     * @return account
     */
    public Optional<Account> account() {
        return getAttr("account");
    }
    
    /**
     * @param loginId login id
     * @param password password
     * @return true if success, else failure
     */
    public boolean login(String loginId, String password) {
        if("admin".equals(loginId)) {
            setAttr("account", new Account(loginId, loginId, Administrator.class));
            return true;
        }
        return false;
    }
    
    /**
     * @return login id
     */
    public String logout() {
        String result = account().orElse(Account.GUEST).id;
        raw.invalidate();
        return result;
    }
}
