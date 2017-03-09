package framework;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import framework.annotation.Only.User;
import framework.annotation.Required;

/**
 * account info
 */
@SuppressWarnings("serial")
public class Account implements Serializable {

    /**
     * user for not login
     */
    public static final Account GUEST = new Account("guest", "guest", Tool.array());

    /**
     * id
     */
    @Required
    public final String id;

    /**
     * name
     */
    @Required
    public final String name;

    /**
     * role
     */
    public final Class<? extends User>[] roles;

    /**
     * @param id id
     * @param name name
     * @param roles roles
     */
    protected Account(String id, String name, Class<? extends User>[] roles) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.roles = Objects.requireNonNull(roles);
    }

    /**
     * Builder
     */
    @SuppressWarnings("javadoc")
    public static class Builder extends AbstractBuilder<Account, Builder> {
        enum F {
            id, name, roles,
        }

        public Builder() {
            super(F.class, Account.class);
        }

        public Builder id(String id) {
            return set(F.id, id);
        }

        public Builder name(String name) {
            return set(F.name, name);
        }

        @SafeVarargs
        public final Builder roles(Class<? extends User>... roles) {
            return set(F.roles, roles);
        }
    }

    /**
     * @return builder
     */
    public static Builder of() {
        return new Builder();
    }

    /**
     * @param roles roles
     * @return true if has role
     */
    public boolean hasAnyRole(@SuppressWarnings("unchecked") Class<? extends User>... roles) {
        if (this.roles != null) {
            for (Class<? extends User> i : this.roles) {
                for (Class<? extends User> j : roles) {
                    if (i == j) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param loginId login id
     * @param password password
     * @return account or empty if login failed
     */
    public static Optional<Account> loginWithConfig(String loginId, String password) {
        return Config.app_accounts.stream().map(i -> i.split(":")).filter(a -> a[0].equals(loginId) && a[1].equals(password)).findFirst()
                .map(a -> new Account(loginId, a[2], Tool.string(a[3]).map(User::fromString).map(Tool::array).orElse(Tool.array())));
    }

    /**
     * @param loginId login id
     * @param password password
     * @return account or empty if login failed
     */
    public static Optional<Account> loginWithDb(String loginId, String password) {
        try (Db db = Db.connect()) {
            return db.queryFile("login.sql", Tool.jsonMap("id", loginId, "password", password)).findFirst().map(Try
                    .f(rs -> new Account(loginId, rs.getString(1), Tool.string(rs.getString(2)).map(User::fromString).map(Tool::array).orElse(Tool.array()))));
        }
    }
}
