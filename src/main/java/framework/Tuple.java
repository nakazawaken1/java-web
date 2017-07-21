package framework;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Tuple
 * 
 * @param <L> Left type
 * @param <R> Right type
 */
public class Tuple<L, R> implements Map.Entry<L, R> {

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

    @Override
    public L getKey() {
        return l;
    }

    @Override
    public R getValue() {
        return r;
    }

    @Override
    public R setValue(R value) {
        throw new UnsupportedOperationException();
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
     * @return List
     */
    public List<Object> toList() {
        List<Object> list = Tool.list(l);
        if (r instanceof Tuple) {
            list.addAll(((Tuple<?, ?>) r).toList());
        } else if (r != null) {
            list.add(r);
        }
        return list;
    }

    /**
     * @param left left consumer
     * @param right right consumer
     */
    public void accept(Consumer<L> left, Consumer<R> right) {
        if (l != null) {
            left.accept(l);
        }
        if (r != null) {
            right.accept(r);
        }
    }

    /**
     * @param <T> result type
     * @param mapper mapper
     * @return result
     */
    public <T> T map(BiFunction<L, R, T> mapper) {
        return mapper.apply(l, r);
    }

    /**
     * @param <T> return type
     * @param i index
     * @return value
     */
    @SuppressWarnings("unchecked")
    public <T> T at(int i) {
        Tuple<?, ?> value = this;
        while (--i >= 0) {
            value = (Tuple<?, ?>) value.r;
        }
        return (T) value.l;
    }

    /**
     * 1 tuple
     * 
     * @param <A> 1 type
     */
    public static class Tuple1<A> extends Tuple<A, Void> {
        /**
         * @param a 1 value
         */
        public Tuple1(A a) {
            super(a, null);
        }

    }

    /**
     * @param <A> first value type
     * @param a 1 value
     * @return 1 tuple
     */
    public static <A> Tuple1<A> of(A a) {
        return new Tuple1<>(a);
    }

    /**
     * @param <A> first value type
     * @param <B> second value type
     * @param a 1 value
     * @param b 2 value
     * @return 2 tuple
     */
    public static <A, B> Tuple<A, B> of(A a, B b) {
        return new Tuple<>(a, b);
    }

    /**
     * 3 tuple
     * 
     * @param <A> first value type
     * @param <B> second value type
     * @param <C> third value type
     */
    public static class Tuple3<A, B, C> extends Tuple<A, Tuple<B, C>> {
        /**
         * @param a 1 value
         * @param b 2 value
         * @param c 3 value
         */
        public Tuple3(A a, B b, C c) {
            super(a, new Tuple<>(b, c));
        }
    }

    /**
     * @param <A> first value type
     * @param <B> second value type
     * @param <C> third value type
     * @param a 1 value
     * @param b 2 value
     * @param c 3 value
     * @return 3 tuple
     */
    public static <A, B, C> Tuple3<A, B, C> of(A a, B b, C c) {
        return new Tuple3<>(a, b, c);
    }

    /**
     * 4 tuple
     * 
     * @param <A> 1 type
     * @param <B> 2 type
     * @param <C> 3 type
     * @param <D> 4 type
     */
    public static class Tuple4<A, B, C, D> extends Tuple<A, Tuple3<B, C, D>> {
        /**
         * @param a 1 value
         * @param b 2 value
         * @param c 3 value
         * @param d 4 value
         */
        public Tuple4(A a, B b, C c, D d) {
            super(a, new Tuple3<>(b, c, d));
        }
    }

    /**
     * @param <A> 1 type
     * @param <B> 2 type
     * @param <C> 3 type
     * @param <D> 4 type
     * @param a 1 value
     * @param b 2 value
     * @param c 3 value
     * @param d 4 value
     * @return 4 tuple
     */
    public static <A, B, C, D> Tuple4<A, B, C, D> of(A a, B b, C c, D d) {
        return new Tuple4<>(a, b, c, d);
    }

    /**
     * @param args not use
     */
    public static void main(String[] args) {
        Consumer<Object> echo = System.out::println;
        echo.accept(Tuple.of(1).equals(Tuple.of(Integer.parseInt("1"))));
        echo.accept(Tuple.of(1, 2).equals(Tuple.of(1, 2)));
        echo.accept(Tuple.of(1, 2, 3).equals(Tuple.of(1, 2, 3)));
        java.util.stream.IntStream.rangeClosed(1, 100).mapToObj(i -> Tuple.of(i % 3 == 0 ? "Fizz" : "", i))
                .map(t -> Tuple.of(t.l + (t.r % 5 == 0 ? "Buzz" : ""), t.r)).map(t -> t.r + ": " + (t.l.isEmpty() ? t.r : t.l)).forEach(echo);
    }
}
