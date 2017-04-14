package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Function;

import framework.Reflector;

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

    /**
     * field annotation getter
     */
    Function<Field, Optional<Size>> FIELD = Reflector.annotation(Size.class);
}
