package framework;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;

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
            Tool.getLogger().info("logged in : " + loginId + Arrays.toString(i.roles));
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
