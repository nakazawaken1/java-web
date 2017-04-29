package framework;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;

import app.config.Sys;
import app.model.Account;

/**
 * session scoped object
 */
public abstract class Session implements Attributes<Serializable> {

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
     * @param loginId login id
     * @param password password
     * @return true if success, else failure
     */
    public boolean login(String loginId, String password) {
        Optional<Account> a = Try
                .s(() -> Tool.<Optional<Account>>invoke(Sys.login_method, Tool.array(String.class, String.class), loginId, password), e -> {
                    Log.warning(() -> Tool.print(e::printStackTrace));
                    return Optional.<Account>empty();
                }).get();
        return Tool.ifPresentOr(a, i -> {
            setAttr(sessionKey, i);
            Log.info("logged in : " + loginId + Arrays.toString(i.roles));
        }, () -> Log.info("login failed: " + loginId));
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
