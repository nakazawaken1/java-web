package app.model;

import java.io.Serializable;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import app.config.Sys;
import framework.AbstractBuilder;
import framework.Db;
import framework.Tool;
import framework.Tool.Traverser;
import framework.Try;
import framework.annotation.Help;
import framework.annotation.Mapping;
import framework.annotation.Only.User;
import framework.annotation.Persist;
import framework.annotation.Required;
import framework.annotation.Stringer;

/**
 * account info
 */
@SuppressWarnings("serial")
@Mapping("t_account")
@Help("アカウント")
@Persist
public class Account implements Serializable {

    /**
     * user for not login
     */
    public static final Account GUEST = new Account("guest", "テスト所属 職員番号 テスト氏名", Tool.array(), null);

    /**
     * id
     */
    @Required
    @Mapping("login_id")
    @Help("ログインID")
    public final String id;

    /**
     * name
     */
    @Required
    @Help("表示名")
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
    @Help("権限")
    public final Class<? extends User>[] roles;

    /**
     * アバター
     */
    @Help("アバター")
    public final String avator;

    /**
     * @param id Id
     * @param name Name
     * @param roles Roles
     * @param avator Avator
     */
    protected Account(String id, String name, Class<? extends User>[] roles, String avator) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.roles = roles == null ? Tool.array() : roles;
        this.avator = avator;
    }

    /**
     * Builder
     */
    @SuppressWarnings("javadoc")
    public static class Builder extends AbstractBuilder<Account, Builder, Builder.Fields> {
        enum Fields {
            id,
            name,
            roles,
            avator,
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
     * Instantiate from array
     * 
     * @param loginId loginId
     * @return Function of "Array" to "Account"
     */
    public static Function<String[], Account> fromArray(String loginId) {
        return a -> new Account(loginId, Tool.at(a, 2).orElse(loginId), Tool.at(a, 3).map(User::fromString).map(Tool::array).orElseGet(Tool::array),
                Tool.at(a, 4).orElse(Sys.default_avator));
    }

    /**
     * Instantiate from ResultSet
     * 
     * @param loginId loginId
     * @return Function of "ResultSet" to "Account"
     */
    @SuppressWarnings("unchecked")
    public static Function<ResultSet, Account> fromResultSet(String loginId) {
        return Try.f(rs -> {
            int max = rs.getMetaData().getColumnCount();
            return new Account(loginId,
                    max < 1 ? loginId : rs.getString(1), Tool.string(max < 2 ? null : rs.getString(2))
                            .map(s -> Stream.of(s.split(",")).map(User::fromString).toArray(Class[]::new)).orElseGet(Tool::array),
                    Tool.string(max < 3 ? null : rs.getString(3)).orElse(Sys.default_avator));
        });
    }

    /**
     * @param loginId login id
     * @param password password
     * @return account or empty if login failed
     */
    public static Optional<Account> loginWithConfig(String loginId, String password) {
        return Sys.accounts.stream().map(Tool.bindRight(String::split, ":")).filter(a -> a[0].equals(loginId) && a[1].equals(password)).findFirst()
                .map(fromArray(loginId));
    }

    /**
     * @param loginId login id
     * @param password password
     * @return account or empty if login failed
     */
    public static Optional<Account> loginWithDb(String loginId, String password) {
        try (Db db = Db.connect()) {
            return db.queryFile("login.sql", Tool.map("id", loginId, "password", password)).findFirst().map(fromResultSet(loginId));
        }
    }
}
