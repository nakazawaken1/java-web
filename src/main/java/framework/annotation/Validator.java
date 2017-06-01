package framework.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Stream;

import framework.AbstractValidator;
import framework.Reflector;
import framework.Tool;
import framework.Try;

/**
 * Validator info
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Validator {
    /**
     * @return apply group
     */
    Class<? extends AbstractValidator<?>> value();

    /**
     * Factory constructor
     */
    public class Constructor {
        /**
         * @param <T> Validate class type
         * @param annotation Target annotation
         * @return Validator instance
         */
        @SuppressWarnings("unchecked")
        public static <T extends Annotation> AbstractValidator<T> instance(T annotation) {
            Class<? extends Annotation> c = annotation.annotationType();
            Class<? extends AbstractValidator<T>> d = (Class<? extends AbstractValidator<T>>) Tool.of(c.getAnnotation(Validator.class))
                    .<Class<?>>map(Validator::value)
                    .orElseGet(() -> Stream.of(c.getDeclaredClasses()).filter(i -> AbstractValidator.class.isAssignableFrom(i)).findFirst().get());
            return Reflector.constructor(d, c).map(Try.f(i -> i.newInstance(annotation))).get();
        }
    }
}
