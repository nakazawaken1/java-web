package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * publish to web
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Route {

    /**
     * @return path
     */
    String value() default "";

    /**
     * @return allowed method(not check if empty)
     */
    Method[] method() default {};

    /**
     * http method
     */
    enum Method {
        /**
         * GET
         */
        GET,
        /**
         * POST
         */
        POST,
        /**
         * PUT
         */
        PUT,
        /**
         * DELETE
         */
        DELETE,
        /**
         * HEAD
         */
        HEAD,
        /**
         * OPTIONS
         */
        OPTIONS,
        /**
         * TRACE
         */
        TRACE,
        /**
         * CONNECT
         */
        CONNECT,
        /**
         * PATCH
         */
        PATCH,
    }
}
