package framework;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Closer
 * 
 * @param <T> close targer
 */
public class Closer<T extends AutoCloseable> implements AutoCloseable {
    /**
     * target
     */
    Optional<T> target = Optional.empty();

    /**
     * @param target target
     * @return target
     */
    public T set(T target) {
        this.target = Optional.ofNullable(target);
        return target;
    }

    /**
     * @return target
     */
    public Optional<T> get() {
        return target;
    }

    /**
     * close
     */
    @Override
    public void close() {
        target.ifPresent(i -> {
            try {
                i.close();
            } catch (Exception e) {
                Logger.getGlobal().log(Level.WARNING, "close error", e);
            }
        });
    }
}
