package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.AbstractValidator;
import framework.annotation.Validator.ErrorAppender;

/**
 * require to input
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Validator(Required.Validator.class)
public @interface Required {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default Valid.All.class;

    /**
     * @return Default value(empty if non default value)
     */
    String[] value() default {};

    /**
     * @return Error message
     */
    String message() default "{Sys.Alert.required}";

    @SuppressWarnings("javadoc")
    class Validator extends AbstractValidator<Required> {

        public Validator(Required annotation) {
            super(annotation);
        }

        @Override
        protected void validate(String name, String value, ErrorAppender appender) {
            if (value == null || value.isEmpty()) {
                appender.addError(name, value, annotation.message());
            }
        }
    }
}
