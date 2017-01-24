package framework;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.servlet.http.HttpSession;

/**
 * session scoped object
 */
public class Session implements Attributes<Object> {

    /**
     * current session
     */
    transient final HttpSession raw;

    /**
     * session key of account
     */
    public static final String sessionKey = "\naccount\n";

    /**
     * getters
     */
    static final Getters getters = new Getters(Session.class);

    /**
     * constructor
     */
    Session() {
        raw = Request.request.get().getSession();
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
        raw.invalidate();
        return result;
    }
}
