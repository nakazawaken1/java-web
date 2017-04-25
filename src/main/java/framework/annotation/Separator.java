package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * separator
 */
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Separator {

    /**
     * @return format
     */
    char value() default ',';

    /**
     * @return prefix
     */
    String prefix() default "\\s*[";

    /**
     * @return suffix
     */
    String suffix() default "]\\s*";

    /**
     * @return pair separator(for map)
     */
    char pair() default 0;
}
