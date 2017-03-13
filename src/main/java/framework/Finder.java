package framework;

import java.util.stream.Stream;

/**
 * Db to object
 * @param <T> target class
 */
public class Finder<T> {
    /**
     * target class
     */
    Class<T> clazz;
    /**
     * @param <U> target class type
     * @param clazz target class
     * @return finder
     */
    public static <U> Finder<U> of(Class<U> clazz) {
        return new Finder<U>(clazz);
    }
    /**
     * @param clazz
     */
    public Finder(Class<T> clazz) {
        this.clazz = clazz;
    }
    /**
     * @return all record
     */
    public Stream<T> all() {
        try(Db db = Db.connect()) {
            return db.query(clazz);
        }
    }
}
