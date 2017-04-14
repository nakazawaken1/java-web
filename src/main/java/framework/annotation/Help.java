package framework.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Function;

import framework.Reflector;

/**
 * Help document
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Help {

    /**
     * @return document
     */
    String[] value();

    /**
     * field annotation getter
     */
    Function<Field, Optional<Help>> FIELD = Reflector.annotation(Help.class);
}
