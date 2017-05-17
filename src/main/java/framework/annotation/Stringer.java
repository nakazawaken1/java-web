package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.Tool.Traverser;

/**
 * Value from string
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Stringer {
    /**
     * @return value
     */
    Class<? extends FromTo<?>> value();

    /**
     * From string and to string
     * 
     * @param <T> Value type
     */
    interface FromTo<T> {
        /**
         * @param text Text
         * @return Value
         */
        T fromString(String text);

        /**
         * @param value Value
         * @param traverser Callback handler
         */
        void toString(T value, Traverser traverser);
    }
}
