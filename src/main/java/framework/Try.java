package framework;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

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

            /*
             * (non-Javadoc)
             * 
             * @see java.util.function.Consumer#accept(java.lang.Object)
             */
            @Override
            public void accept(T t) {
                try {
                    consumer.accept(t);
                } catch (RuntimeException e) {
                    throw e;
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
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * throwable consumer
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
    public static <R> IntFunction<R> f(TryIntFunction<R> function) {
        return new IntFunction<R>() {

            @Override
            public R apply(int t) {
                try {
                    return function.apply(t);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
