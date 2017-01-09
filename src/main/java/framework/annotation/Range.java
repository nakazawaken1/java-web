package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * value range limitation
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default {};

    /**
     * @return minimum number
     */
    double min() default Double.NEGATIVE_INFINITY;

    /**
     * @return maximum number
     */
    double value();

    /**
     * @return minimum length of integer part
     */
    int integerMin() default 0;

    /**
     * @return maximum length of integer part
     */
    int integerMax() default Integer.MAX_VALUE;

    /**
     * @return minimum length of fraction part
     */
    int fractionMin() default 0;

    /**
     * @return maximum length of fraction part
     */
    int fractionMax() default Integer.MAX_VALUE;
}
