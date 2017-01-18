package framework;

import framework.annotation.Only.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

/**
 * account info
 */
@Value
public class Account {

    /**
     * user for not login
     */
    public static final Account GUEST = new Account("0", "(guest)");

    /**
     * id
     */
    String id;
    /**
     * name
     */
    String name;
    /**
     * role
     */
    @Getter(AccessLevel.NONE)
    Class<? extends User>[] roles;

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
