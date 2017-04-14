package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * accept type
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Accept {

    /**
     * @return accept mime type at request
     */
    String[] value() default {};

    /**
     * application/x-www-form-urlencoded
     */
    String FORM = "application/x-www-form-urlencoded";

    /**
     * multipart/form-data
     */
    String MULTIPART = "multipart/form-data";

    /**
     * application/xml
     */
    String XML = "application/xml";

    /**
     * application/json
     */
    String JSON = "application/json";

    /**
     * text/csv
     */
    String CSV = "text/csv";
}
