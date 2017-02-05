package framework;

import java.util.function.Consumer;

/**
 * use any object at try with resources
 * @param <T> object type
 */
public class Defer<T> implements AutoCloseable {
    /**
     * instance
     */
    T instance;
    /**
     * close action
     */
    Consumer<T> close;
    /**
     * @param instance instance
     * @param close close
     */
    public Defer(T instance, Consumer<T> close) {
        this.instance = instance;
        this.close = close;
    }
    
    /* (non-Javadoc)
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() {
        close.accept(instance);
    }

    /**
     * @return instance
     */
    public T get() {
        return instance;
    }
}
