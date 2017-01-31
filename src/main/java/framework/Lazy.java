package framework;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Instantiate when first reference
 *
 * @param <T>
 */
public class Lazy<T> implements AutoCloseable {
    /**
     * supplier
     */
    Supplier<T> supplier;
    /**
     * instance
     */
    Optional<T> instance = Optional.empty();

    /**
     * @param supplier supplier
     */
    public Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * @return instance
     */
    public T get() {
        return instance.orElseGet(supplier);
    }

    /**
     * @param consumer action when got instance
     */
    public void ifGot(Consumer<T> consumer) {
        instance.ifPresent(consumer);
    }

    /* (non-Javadoc)
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() {
        instance.filter(i -> i instanceof AutoCloseable).ifPresent(Try.c(i -> ((AutoCloseable) i).close(), e -> Logger.getGlobal().log(Level.WARNING, "close error", e)));
    }
}
