package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.AbstractValidator;
import framework.Try;

/**
 * length limitation
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Size {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default Valid.All.class;

    /**
     * @return minimum length
     */
    int min() default 1;

    /**
     * @return maximum length
     */
    int value();

    /**
     * @return If empty, check character count else byte count
     */
    String charset() default "";

    /**
     * @return Error message
     */
    String message() default "{Sys.Alert.size}";

    @SuppressWarnings("javadoc")
    class Validator extends AbstractValidator<Size> {

        public Validator(Size annotation) {
            super(annotation);
        }

        @Override
        protected void validate(String name, String value, ErrorAppender appender) {
            if (value == null || value.isEmpty()) {
                return;
            }
            int length = Try.s(() -> annotation.charset().isEmpty() ? value.length() : value.getBytes(annotation.charset()).length).get();
            if (length < annotation.min() || annotation.value() < length) {
                appender.addError(name, value, annotation.message(), "min", annotation.min(), "value", annotation.value());
            }
        }
    }
}
