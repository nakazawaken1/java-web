package framework;

import java.util.stream.Stream;

import framework.annotation.Only;
import framework.annotation.Only.User;

/**
 * Database account
 */
@SuppressWarnings("serial")
public class DbAccount extends Account {

    /**
     * @param loginId loginId
     * @param password password
     * @throws InstantiationException login error
     */
    @SuppressWarnings("unchecked")
    public DbAccount(String loginId, String password) throws InstantiationException {
        super(loginId, null, null);
        try (Db db = Db.connect()) {
            if (!db.select("name", "roles").from("t_account").where("login_id", loginId).where("password", Tool.hash(password)).row(rs -> {
                name = rs.getString(1);
                roles = Tool
                        .string(rs.getString(2)).map(s -> Stream.of(s.split("\\s*,\\s*"))
                                .map(Try.f(role -> (Class<? extends User>) Class.forName(Only.class.getName() + "$" + role))).toArray(Class[]::new))
                        .orElse(null);
            })) {
                throw new InstantiationException();
            }
        }
    }

}
