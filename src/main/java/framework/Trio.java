package framework;

/**
 * 3 tuple
 * @param <T> first type
 * @param <U> second type
 * @param <V> third type
 */
public class Trio<T, U, V> {
    /**
     * first value
     */
    public final T a;
    /**
     * second value
     */
    public final U b;
    /**
     * third value
     */
    public final V c;
    /**
     * constructor
     * @param a first value
     * @param b second value
     * @param c third value
     */
    public Trio(T a, U b, V c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }
}
