package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * restrict viewer
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Only {
    /**
     * @return role(if empty then logged in user only)
     */
    Class<? extends User>[] value() default {};

    /**
     * user
     */
    public interface User {
    }

    /**
     * administrator
     */
    public interface Administrator extends User {
    }
}
