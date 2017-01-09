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
    public interface All {
    }

    /**
     * group for creation
     */
    public interface Create extends All {
    }

    /**
     * group for update
     */
    public interface Update extends All {
    }

    /**
     * group for deletion
     */
    public interface Delete extends All {
    }

    /**
     * group for creation & update
     */
    public interface Save extends Create, Update {
    }
}
