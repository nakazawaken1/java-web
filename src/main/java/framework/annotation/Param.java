package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * bind to request parameter
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    /**
     * Parameter type
     */
    enum Type {
        /**
         * Query string
         */
        QUERY,
        /**
         * URL path
         */
        PATH,
        /**
         * Form data
         */
        FORM,
        /**
         * Cookie
         */
        COOKIE,
        /**
         * Request header
         */
        HEADER,
        /**
         * Request attribute
         */
        REQUEST,
        /**
         * Session attribute
         */
        SESSION,
        /**
         * Application attribute
         */
        APPLICATION,
    }

    /**
     * @return parameter type
     */
    Type[] value() default { Type.QUERY, Type.FORM };

    /**
     * @return parameter name(use method parameter name if empty)
     */
    String name() default "";
}
