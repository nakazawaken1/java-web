package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * enable validation
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Valid {
    /**
     * @return apply group
     */
    Class<? extends All> value() default All.class;

    /**
     * default group
     */
    interface All {
    }

    /**
     * group for creation
     */
    interface Create extends All {
    }

    /**
     * group for read
     */
    interface Read extends All {
    }

    /**
     * group for update
     */
    interface Update extends All {
    }

    /**
     * group for deletion
     */
    interface Delete extends All {
    }

    /**
     * group for creation &amp; update
     */
    interface Save extends Create, Update {
    }
}
