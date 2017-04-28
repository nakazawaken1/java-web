package framework;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Instantiate when first reference
 *
 * @param <T> target type
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
    public synchronized T get() {
        T value = instance.orElseGet(() -> supplier == null ? null : supplier.get());
        if (!instance.isPresent()) {
            instance = Tool.of(value);
        }
        return value;
    }

    /**
     * @param value value
     */
    public synchronized void set(T value) {
        if (supplier != null) {
            throw new UnsupportedOperationException();
        }
        instance = Tool.of(value);
    }

    /**
     * @param consumer action when got instance
     * @return self
     */
    public Lazy<T> ifGot(Consumer<T> consumer) {
        instance.ifPresent(consumer);
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() {
        instance.filter(i -> i instanceof AutoCloseable).ifPresent(Try.c(i -> ((AutoCloseable) i).close(), (e, i) -> Log.warning(e, () -> "close error")));
    }
}
