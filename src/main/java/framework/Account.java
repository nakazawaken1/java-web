package framework;

import java.io.Serializable;

import framework.annotation.Only.User;

/**
 * account info
 */
@SuppressWarnings("serial")
public class Account implements Serializable {

    /**
     * user for not login
     */
    public static final Account GUEST = new Account("guest", "guest", null);

    /**
     * id
     */
    final String id;
    /**
     * name
     */
    final String name;
    /**
     * role
     */
    final Class<? extends User>[] roles;

    /**
     * @param id id
     * @param name name
     * @param roles roles
     */
    protected Account(String id, String name, Class<? extends User>[] roles) {
        this.id = id;
        this.name = name;
        this.roles = roles;
    }

    /**
     * @param loginId login id
     * @param password password
     * @throws InstantiationException login error
     */
    @SuppressWarnings("unchecked")
    public Account(String loginId, String password) throws InstantiationException {
        String[] a = Config.app_accounts.stream().map(i -> i.split(":")).filter(i -> i[0].equals(loginId) && i[1].equals(password)).findFirst()
                .orElseThrow(InstantiationException::new);
        this.id = a[0];
        this.name = a[0];
        this.roles = Tool.string(a[2]).map(Try.f(i -> Tool.array((Class<? extends User>) Class.forName(i)))).orElse(null);
    }

    /**
     * @param roles roles
     * @return true if has role
     */
    public boolean hasAnyRole(@SuppressWarnings("unchecked") Class<? extends User>... roles) {
        if (this.roles != null) {
            for (Class<?> i : this.roles) {
                for (Class<?> j : roles) {
                    if (i == j) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * @return name
     */
    public String getName() {
        return name;
    }
}
