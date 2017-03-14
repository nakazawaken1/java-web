package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Initial Data
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InitialData {
    /**
     * @return field names(for insert table(?))
     */
    String field();

    /**
     * @return values(for insert table(...) values(?))
     */
    String[] values();
}
