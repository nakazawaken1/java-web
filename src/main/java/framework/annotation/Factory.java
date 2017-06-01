package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.AbstractBuilder;
import framework.Reflector;
import framework.Tool;

/**
 * Factory info
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Factory {
    /**
     * @return apply group
     */
    Class<? extends AbstractBuilder<?, ?, ?>> value();

    /**
     * Factory constructor
     */
    public class Constructor {
        /**
         * @param <T> Target class type
         * @param targetClass Target class
         * @return Target factory instance
         */
        @SuppressWarnings("unchecked")
        public static <T> AbstractBuilder<T, ?, ?> instance(Class<T> targetClass) {
            return (AbstractBuilder<T, ?, ?>) Tool.of(targetClass.getAnnotation(Factory.class)).map(Factory::value).map(Reflector::instance)
                    .orElseGet(() -> Reflector.instance(targetClass.getName() + "$Builder"));
        }
    }
}
