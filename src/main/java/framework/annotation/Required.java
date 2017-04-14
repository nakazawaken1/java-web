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
 * require to input
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Required {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] value() default {};

    /**
     * field annotation getter
     */
    Function<Field, Optional<Required>> FIELD = Reflector.annotation(Required.class);
}
