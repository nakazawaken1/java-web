package framework;

import java.sql.SQLException;

/**
 * unchecked sql exception
 */
@SuppressWarnings("serial")
public class UncheckedSQLException extends RuntimeException {
    /**
     * @param e sql exception
     */
    public UncheckedSQLException(SQLException e) {
        super(e);
    }

    /**
     * @param message message
     */
    public UncheckedSQLException(String message) {
        super(message);
    }
}
