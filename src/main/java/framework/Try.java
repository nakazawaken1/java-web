package framework;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * support lambda exception
 */
public class Try {
    
    /**
     * Exception catcher
     */
    public static final Consumer<Exception> catcher = e -> {
        if(e instanceof RuntimeException) {
            throw (RuntimeException)e;
        }
        if(e instanceof IOException) {
            throw new UncheckedIOException((IOException)e);
        }
        if(e instanceof SQLException) {
            throw new UncheckedSQLException((SQLException)e);
        }
        throw new RuntimeException(e);
    };

    /**
     * throwable runnable
     */
    @FunctionalInterface
    public static interface TryRunnable {
        /**
         * @throws Exception exception
         */
        void run() throws Exception;
    }

    /**
     * @param runnable throwable runnable
     * @param error error action
     * @return runnable
     */
    public static Runnable r(TryRunnable runnable, Consumer<Exception> error) {
        return () -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    error.accept(e);
                }
        };
    }
    
    /**
     * @param runnable throwable runnable
     * @return runnable
     */
    public static Runnable r(TryRunnable runnable) {
        return r(runnable, catcher);
    }

    /**
     * throwable consumer
     *
     * @param <T> object type
     */
    @FunctionalInterface
    public static interface TryConsumer<T> {
        /**
         * @param t object
         * @throws Exception exception
         */
        void accept(T t) throws Exception;
    }

    /**
     * @param <T> value type
     * @param consumer throwable consumer
     * @param error error action
     * @return consumer
     */
    public static <T> Consumer<T> c(TryConsumer<T> consumer, Consumer<Exception> error) {
        return value -> {
                try {
                    consumer.accept(value);
                } catch (Exception e) {
                    error.accept(e);
                }
        };
    }

    /**
     * @param <T> value type
     * @param consumer throwable consumer
     * @return consumer
     */
    public static <T> Consumer<T> c(TryConsumer<T> consumer) {
        return c(consumer, catcher);
    }

    /**
     * throwable consumer
     */
    @FunctionalInterface
    public static interface TryIntConsumer {
        /**
         * @param t object
         * @throws Exception exception
         */
        void accept(int t) throws Exception;
    }

    /**
     * @param consumer throwable consumer
     * @param error error action
     * @return consumer
     */
    public static IntConsumer intC(TryIntConsumer consumer, Consumer<Exception> error) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                error.accept(e);
            }
        };
    }

    /**
     * @param consumer throwable consumer
     * @return consumer
     */
    public static IntConsumer intC(TryIntConsumer consumer) {
        return intC(consumer, catcher);
    }
    
    /**
     * throwable consumer
     *
     * @param <T> first object type
     * @param <U> second object type
     */
    @FunctionalInterface
    public static interface TryBiConsumer<T, U> {
        /**
         * @param t first object
         * @param u second object
         * @throws Exception exception
         */
        void accept(T t, U u) throws Exception;
    }

    /**
     * @param <T> first object type
     * @param <U> second object type
     * @param consumer throwable consumer
     * @param error error action
     * @return consumer
     */
    public static <T, U> BiConsumer<T, U> c(TryBiConsumer<T, U> consumer, Consumer<Exception> error) {
        return (t, u) -> {
            try {
                consumer.accept(t, u);
            } catch (Exception e) {
                error.accept(e);
            }
        };
    }

    /**
     * @param <T> first object type
     * @param <U> second object type
     * @param consumer throwable consumer
     * @return consumer
     */
    public static <T, U> BiConsumer<T, U> c(TryBiConsumer<T, U> consumer) {
        return c(consumer, catcher);
    }

    /**
     * throwable consumer
     *
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     */
    @FunctionalInterface
    public static interface TriConsumer<T, U, V> {
        /**
         * @param t object
         * @param u other object
         * @param v other object
         */
        void accept(T t, U u, V v);
    }

    /**
     * throwable consumer
     *
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     */
    @FunctionalInterface
    public static interface TryTriConsumer<T, U, V> {
        /**
         * @param t object
         * @param u other object
         * @param v other object
         * @throws Exception exception
         */
        void accept(T t, U u, V v) throws Exception;
    }

    /**
     * @param consumer consumer
     * @param error error action
     * @return consumer
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     */
    public static <T, U, V> TriConsumer<T, U, V> c(TryTriConsumer<T, U, V> consumer, Consumer<Exception> error) {
        return (t, u, v) -> {
            try {
                consumer.accept(t, u, v);
            } catch (Exception e) {
                error.accept(e);
            }
        };
    }

    /**
     * @param consumer consumer
     * @return consumer
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     */
    public static <T, U, V> TriConsumer<T, U, V> c(TryTriConsumer<T, U, V> consumer) {
        return c(consumer, catcher);
    }

    /**
     * throwable consumer
     *
     * @param <T> object type
     */
    @FunctionalInterface
    public static interface TryObjIntConsumer<T> {
        /**
         * @param t object
         * @param u int value
         * @throws Exception exception
         */
        void accept(T t, int u) throws Exception;
    }

    /**
     * @param <T> object type
     * @param consumer throwable consumer
     * @param error error action
     * @return consumer
     */
    public static <T> ObjIntConsumer<T> intC(TryObjIntConsumer<T> consumer, Consumer<Exception> error) {
        return (t, u) -> {
            try {
                consumer.accept(t, u);
            } catch (Exception e) {
                error.accept(e);
            }
        };
    }

    /**
     * @param <T> object type
     * @param consumer throwable consumer
     * @return consumer
     */
    public static <T> ObjIntConsumer<T> intC(TryObjIntConsumer<T> consumer) {
        return intC(consumer, catcher);
    }

    /**
     * throwable function
     * 
     * @param <A> argument type
     * @param <R> return object type
     */
    @FunctionalInterface
    public static interface TryFunction<A, R> {
        /**
         * @param t object
         * @return value
         * @throws Exception exception
         */
        R apply(A t) throws Exception;
    }

    /**
     * @param <A> argument type
     * @param <R> return type
     * @param function throwable function
     * @param error error action
     * @return function
     */
    public static <A, R> Function<A, R> f(TryFunction<A, R> function, Consumer<Exception> error) {
        return argument -> {
            try {
                return function.apply(argument);
            } catch (Exception e) {
                error.accept(e);
                return null;
            }
        };
    }

    /**
     * @param <A> argument type
     * @param <R> return type
     * @param function throwable function
     * @return function
     */
    public static <A, R> Function<A, R> f(TryFunction<A, R> function) {
        return f(function, catcher);
    }

    /**
     * throwable function
     *
     * @param <R> return object type
     */
    @FunctionalInterface
    public static interface TryIntFunction<R> {
        /**
         * @param t object
         * @return value
         * @throws Exception exception
         */
        R apply(int t) throws Exception;
    }

    /**
     * @param <R> return type
     * @param function throwable function
     * @param error error action
     * @return function
     */
    public static <R> IntFunction<R> intF(TryIntFunction<R> function, Consumer<Exception> error) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception e) {
                error.accept(e);
                return null;
            }
        };
    }

    /**
     * @param <R> return type
     * @param function throwable function
     * @return function
     */
    public static <R> IntFunction<R> intF(TryIntFunction<R> function) {
        return intF(function, catcher);
    }

    /**
     * throwable function
     *
     * @param <R> return object type
     */
    @FunctionalInterface
    public static interface TrySupplier<R> {
        /**
         * @return value
         * @throws Exception exception
         */
        R get() throws Exception;
    }

    /**
     * @param <R> return type
     * @param function throwable function
     * @param error error action
     * @return function
     */
    public static <R> Supplier<R> s(TrySupplier<R> function, Consumer<Exception> error) {
        return () -> {
            try {
                return function.get();
            } catch (Exception e) {
                error.accept(e);
                return null;
            }
        };
    }

    /**
     * @param <R> return type
     * @param function throwable function
     * @return function
     */
    public static <R> Supplier<R> s(TrySupplier<R> function) {
        return s(function, catcher);
    }

    /**
     * throwable consumer
     *
     * @param <T> object type
     */
    @FunctionalInterface
    public static interface TryPredicate<T> {
        /**
         * @param t object type
         * @return result
         * @throws Exception exception
         */
        boolean test(T t) throws Exception;
    }

    /**
     * @param <T> object type
     * @param predicate predicate
     * @param error error action
     * @return consumer
     */
    public static <T> Predicate<T> p(TryPredicate<T> predicate, Consumer<Exception> error) {
        return t -> {
            try {
                return predicate.test(t);
            } catch (Exception e) {
                error.accept(e);
                return false;
            }
        };
    }

    /**
     * @param <T> object type
     * @param predicate predicate
     * @return consumer
     */
    public static <T> Predicate<T> p(TryPredicate<T> predicate) {
        return p(predicate, catcher);
    }

    /**
     * Tri function
     *
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     * @param <R> other object type
     */
    @FunctionalInterface
    public static interface TriFunction<T, U, V, R> {
        /**
         * @param t object
         * @param u other object
         * @param v other object
         * @return return object
         */
        R apply(T t, U u, V v);
    }

    /**
     * Quad function
     *
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     * @param <W> other object type
     * @param <R> return object type
     */
    @FunctionalInterface
    public static interface QuadFunction<T, U, V, W, R> {
        /**
         * @param t object
         * @param u other object
         * @param v other object
         * @param w other object
         * @return return object
         */
        R apply(T t, U u, V v, W w);
    }
}
