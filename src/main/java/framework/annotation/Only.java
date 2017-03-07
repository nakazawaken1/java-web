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
        
        /**
         * @param text class name
         * @return class
         */
        @SuppressWarnings("unchecked")
        static Class<? extends User> fromString(String text) {
            try {
                return (Class<? extends User>)Class.forName(User.class.getName() + "$" + text);
            } catch (ClassNotFoundException e) {
                try {
                    return (Class<? extends User>)Class.forName(text);
                } catch (ClassNotFoundException e1) {
                    return null;
                }
            } 
        }
    }

    /**
     * administrator
     */
    public interface Administrator extends User {
    }
}
