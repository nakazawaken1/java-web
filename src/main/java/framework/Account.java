package framework;

import framework.annotation.Only.User;

/**
 * account info
 */
public class Account {

    /**
     * user for not login
     */
    public static final Account GUEST = new Account("0", "(guest)");

    @Override
    public String toString() {
        return Tool.dump(this);
    }

    /**
     * id
     */
    public String id;
    /**
     * name
     */
    public String name;
    /**
     * role
     */
    public Class<? extends User>[] roles;

    /**
     * @param id id
     * @param name name
     * @param roles roles
     */
    @SafeVarargs
    public Account(String id, String name, Class<? extends User>... roles) {
        this.id = id;
        this.name = name;
        this.roles = roles;
    }

    /**
     * @param roles roles
     * @return true if has role
     */
    public boolean hasAnyRole(@SuppressWarnings("unchecked") Class<? extends User>... roles) {
        for(Class<?> i : this.roles) {
            for(Class<?> j : roles) {
                if(i == j) {
                    return true;
                }
            }
        }
        return false;
    }
}
