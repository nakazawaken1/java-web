package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * length limitation
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Size {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default {};

    /**
     * @return minimum length
     */
    int min() default 1;

    /**
     * @return maximum length
     */
    int value();

    /**
     * @return length is character count if empty, else byte count
     */
    String charset() default "";
}
