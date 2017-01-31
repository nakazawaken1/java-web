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
import java.util.function.Supplier;

/**
 * support lambda exception
 */
public class Try {
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
     * @return runnable
     */
    public static Runnable r(TryRunnable runnable) {
        return new Runnable() {

            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
     * @param consumer throwable consumer
     * @return consumer
     */
    public static <T> Consumer<T> c(TryConsumer<T> consumer) {
        return new Consumer<T>() {

            @Override
            public void accept(T value) {
                try {
                    consumer.accept(value);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * @param consumer throwable consumer
     * @param fail action if throw exception
     * @return consumer
     */
    public static <T> Consumer<T> c(TryConsumer<T> consumer, Consumer<Exception> fail) {
        return new Consumer<T>() {

            @Override
            public void accept(T value) {
                try {
                    consumer.accept(value);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    fail.accept(e);
                }
            }
        };
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
     * @return consumer
     */
    public static IntConsumer intC(TryIntConsumer consumer) {
        return new IntConsumer() {

            @Override
            public void accept(int t) {
                try {
                    consumer.accept(t);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * throwable consumer
     *
     * @param <T> object type
     * @param <U> another object type
     */
    @FunctionalInterface
    public static interface TryBiConsumer<T, U> {
        /**
         * @param t object
         * @param u another object
         * @throws Exception exception
         */
        void accept(T t, U u) throws Exception;
    }

    /**
     * @param consumer throwable consumer
     * @return consumer
     */
    public static <T, U> BiConsumer<T, U> c(TryBiConsumer<T, U> consumer) {
        return new BiConsumer<T, U>() {

            /*
             * (non-Javadoc)
             * 
             * @see java.util.function.Consumer#accept(java.lang.Object)
             */
            @Override
            public void accept(T t, U u) {
                try {
                    consumer.accept(t, u);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
     * @return consumer
     * @param <T> object type
     * @param <U> other object type
     * @param <V> other object type
     */
    public static <T, U, V> TriConsumer<T, U, V> c(TryTriConsumer<T, U, V> consumer) {
        return new TriConsumer<T, U, V>() {

            /*
             * (non-Javadoc)
             * 
             * @see java.util.function.Consumer#accept(java.lang.Object)
             */
            @Override
            public void accept(T t, U u, V v) {
                try {
                	consumer.accept(t, u, v);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
     * @param consumer throwable consumer
     * @return consumer
     */
    public static <T> ObjIntConsumer<T> intC(TryObjIntConsumer<T> consumer) {
        return new ObjIntConsumer<T>() {

            /*
             * (non-Javadoc)
             * 
             * @see java.util.function.Consumer#accept(java.lang.Object)
             */
            @Override
            public void accept(T t, int u) {
                try {
                    consumer.accept(t, u);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
     * @param function throwable function
     * @return function
     */
    public static <A, R> Function<A, R> f(TryFunction<A, R> function) {
        return new Function<A, R>() {

            @Override
            public R apply(A t) {
                try {
                    return function.apply(t);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
     * @param function throwable function
     * @return function
     */
    public static <R> IntFunction<R> intF(TryIntFunction<R> function) {
        return new IntFunction<R>() {

            @Override
            public R apply(int t) {
                try {
                    return function.apply(t);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
     * @param function throwable function
     * @return function
     */
    public static <R> Supplier<R> s(TrySupplier<R> function) {
        return new Supplier<R>() {

            @Override
            public R get() {
                try {
                    return function.get();
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
