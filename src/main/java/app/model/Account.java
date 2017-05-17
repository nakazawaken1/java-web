package app.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import app.config.Sys;
import framework.AbstractBuilder;
import framework.Db;
import framework.Tool;
import framework.Tool.Traverser;
import framework.Try;
import framework.annotation.Mapping;
import framework.annotation.Only.User;
import framework.annotation.Required;
import framework.annotation.Stringer;

/**
 * account info
 */
@SuppressWarnings("serial")
@Mapping("t_account")
public class Account implements Serializable {

    /**
     * user for not login
     */
    public static final Account GUEST = new Account("guest", "guest", Tool.array());

    /**
     * id
     */
    @Required
    @Mapping("login_id")
    public final String id;

    /**
     * name
     */
    @Required
    public final String name;

    /**
     * Stringer of roles
     */
    static class Roles implements Stringer.FromTo<Class<? extends User>[]> {
        @SuppressWarnings("unchecked")
        @Override
        public Class<? extends User>[] fromString(String text) {
            return Stream.of(text.split("[^0-9a-zA-Z.]+")).map(User::fromString).toArray(Class[]::new);
        }

        @Override
        public void toString(Class<? extends User>[] value, Traverser traverser) {
            Class<?> clazz = Class[].class;
            traverser.start(clazz);
            for (Class<?> i : value) {
                traverser.value(i.getSimpleName(), clazz, true);
            }
            traverser.end(clazz);
        }
    }

    /**
     * role
     */
    @Stringer(Roles.class)
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
            id,
            name,
            roles,
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
    @SafeVarargs
    public final boolean hasAnyRole(Class<? extends User>... roles) {
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
        return Sys.accounts.stream().map(i -> i.split(":")).filter(a -> a[0].equals(loginId) && a[1].equals(password)).findFirst()
                .map(a -> new Account(loginId, a[2], Tool.string(a[3]).map(User::fromString).map(Tool::array).orElse(Tool.array())));
    }

    /**
     * @param loginId login id
     * @param password password
     * @return account or empty if login failed
     */
    public static Optional<Account> loginWithDb(String loginId, String password) {
        try (Db db = Db.connect()) {
            return db.queryFile("login.sql", Tool.map("id", loginId, "password", password)).findFirst().map(Try
                    .f(rs -> new Account(loginId, rs.getString(1), Tool.string(rs.getString(2)).map(User::fromString).map(Tool::array).orElse(Tool.array()))));
        }
    }
}
