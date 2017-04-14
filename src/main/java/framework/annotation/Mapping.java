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
 * database mapping
 */
@Target({ ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Mapping {

    /**
     * @return mapping name(use field name if empty)
     */
    String value() default "";

    /**
     * field annotation getter
     */
    Function<Field, Optional<Mapping>> FIELD = Reflector.annotation(Mapping.class);
}
