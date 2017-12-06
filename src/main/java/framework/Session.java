package framework;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import app.config.Sys;
import app.model.Account;
import framework.annotation.Job;
import framework.annotation.Only.Administrator;

/**
 * session scoped object
 */
@SuppressWarnings("serial")
public abstract class Session implements Attributes<Serializable> {

    /**
     * Locale
     */
    protected Locale locale = null;

    /**
     * @return Locale
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * @return Locale
     */
    public static Locale currentLocale() {
        return current().map(Session::getLocale).filter(Objects::nonNull).orElse(Locale.getDefault());
    }

    /**
     * current session
     */
    transient static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    /**
     * @return current session
     */
    public static Optional<Session> current() {
        return Tool.of(CURRENT.get());
    }

    /**
     * session key of account
     */
    public static final String sessionKey = "$account$";

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
     * @return True if Administrator
     */
    public boolean isAdmin() {
        return getAccount().hasAnyRole(Administrator.class);
    }

    /**
     * @param loginId login id
     * @param password password
     * @return true if success, else failure
     */
    public boolean login(String loginId, String password) {
        Optional<Account> a = Try
                .s(() -> Reflector.<Optional<Account>>invoke(Sys.login_method, Tool.array(String.class, String.class), loginId, password), e -> {
                    Log.warning(() -> Tool.print(e::printStackTrace));
                    return Optional.<Account>empty();
                }).get();
        return Tool.ifPresentOr(a, i -> {
            setAttr(sessionKey, i);
            Job.Scheduler.trigger(Job.OnLoggedIn);
            Log.info("logged in : " + i.id + Arrays.toString(i.roles));
        }, () -> Log.info("login failed: " + loginId));
    }

    /**
     * @return login id
     */
    public String logout() {
        String result = getAccount().id;
        Job.Scheduler.trigger(Job.OnLoggedOut);
        clear();
        Log.info("logged out : " + result);
        return result;
    }
}
