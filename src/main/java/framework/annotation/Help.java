package framework.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Help document
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Help {

    /**
     * @return document
     */
    String[] value();
}
