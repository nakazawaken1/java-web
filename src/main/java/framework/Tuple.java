package framework;

import java.util.Objects;

/**
 * Tuple
 * 
 * @param <L> Left type
 * @param <R> Right type
 */
public class Tuple<L, R> {

    /**
     * left value
     */
    public final L l;

    /**
     * right value
     */
    public final R r;

    /**
     * @param l left value
     * @param r right value
     */
    protected Tuple(L l, R r) {
        this.l = l;
        this.r = r;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(l, r);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Tuple) {
            Tuple<?, ?> base = (Tuple<?, ?>) obj;
            return Objects.equals(l, base.l) && Objects.equals(r, base.r);
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return Objects.toString(l) + (r == null ? "" : ", " + Objects.toString(r));
    }
    
    /**
     * @param i index
     * @return value
     */
    @SuppressWarnings("unchecked")
    public <T> T at(int i) {
        Tuple<?, ?> value = this;
        while(--i >= 0) {
            value = (Tuple<?, ?>)value.r;
        }
        return (T)value.l;
    }

    /**
     * 1 tuple
     * 
     * @param <A> 1 type
     */
    public static class Tuple1<A> extends Tuple<A, Object> {
        /**
         * @param a 1 value
         */
        public Tuple1(A a) {
            super(a, null);
        }

    }

    /**
     * @param a 1 value
     * @return 1 tuple
     */
    public static <A> Tuple1<A> of(A a) {
        return new Tuple1<>(a);
    }

    /**
     * 2 tuple
     * 
     * @param <A> 1 type
     * @param <B> 2 type
     */
    public static class Tuple2<A, B> extends Tuple<A, Tuple1<B>> {
        /**
         * @param a 1 value
         * @param b 2 value
         */
        public Tuple2(A a, B b) {
            super(a, new Tuple1<>(b));
        }
    }

    /**
     * @param a 1 value
     * @param b 2 value
     * @return 2 tuple
     */
    public static <A, B> Tuple2<A, B> of(A a, B b) {
        return new Tuple2<>(a, b);
    }

    /**
     * 3 tuple
     * 
     * @param <A> 1 type
     * @param <B> 2 type
     * @param <C> 3 type
     */
    public static class Tuple3<A, B, C> extends Tuple<A, Tuple2<B, C>> {
        /**
         * @param a 1 value
         * @param b 2 value
         * @param c 3 value
         */
        public Tuple3(A a, B b, C c) {
            super(a, new Tuple2<>(b, c));
        }
    }

    /**
     * @param a 1 value
     * @param b 2 value
     * @param c 3 value
     * @return 3 tuple
     */
    public static <A, B, C> Tuple3<A, B, C> of(A a, B b, C c) {
        return new Tuple3<>(a, b, c);
    }

    /**
     * @param args not use
     */
    public static void main(String[] args) {
        java.util.stream.IntStream.rangeClosed(1, 100)
        .mapToObj(i -> Tuple.of(i % 3 == 0 ? "Fizz" : "", i))
        .map(t -> Tuple.of(t.l + (t.r.l % 5 == 0 ? "Buzz" : ""), t.r.l))
        .map(t -> t.l.isEmpty() ? t.r.l : t.l)
        .forEach(System.out::println);
    }
}
