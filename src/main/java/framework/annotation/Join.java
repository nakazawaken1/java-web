package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * database mapping
 */
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Join {

    /**
     * @return relation table
     */
    String table() default "";

    /**
     * @return from column(:ralation column)
     */
    String[] from() default {};

    /**
     * @return to column(:ralation column)
     */
    String[] to() default {};

    /**
     * @return where(join by and)
     */
    String[] value() default {};
}
