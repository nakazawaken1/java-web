package framework;

import java.util.Map;

/**
 * 2 tuple
 * 
 * @param <T> first type
 * @param <U> second type
 */
public class Pair<T, U> implements Map.Entry<T, U> {
    /**
     * first value
     */
    public final T a;
    /**
     * second value
     */
    public final U b;

    /**
     * constructor
     * 
     * @param a first value
     * @param b second value
     */
    public Pair(T a, U b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public T getKey() {
        return a;
    }

    @Override
    public U getValue() {
        return b;
    }

    @Override
    public U setValue(U value) {
        throw new UnsupportedOperationException();
    }
}
