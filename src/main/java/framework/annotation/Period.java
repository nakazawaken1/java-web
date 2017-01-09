package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * time limitation
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Period {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] value() default {};

    /**
     * @return forward offset
     */
    int past() default Integer.MAX_VALUE;

    /**
     * @return backward offset
     */
    int future() default Integer.MAX_VALUE;

    /**
     * @return unit of past and future
     */
    Unit unit() default Unit.DAYS;

    /**
     * unit
     */
    public enum Unit {
        /**
         * hours
         */
        HOURS,
        /**
         * days
         */
        DAYS,
        /**
         * weeks
         */
        WEEKS,
        /**
         * months
         */
        MONTHS,
        /**
         * quoters
         */
        QUOTERS,
        /**
         * years
         */
        YEARS,
    }
}
