package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import framework.AbstractValidator;

/**
 * Time limitation from past to future
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Time {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default Valid.All.class;

    /**
     * @return forward offset
     */
    int past() default Integer.MAX_VALUE;

    /**
     * @return backward offset
     */
    int future() default Integer.MAX_VALUE;

    /**
     * @return unit of past and future
     */
    ChronoUnit unit() default ChronoUnit.DAYS;

    /**
     * @return Error message
     */
    String message() default "{Sys.Alert.time}";

    @SuppressWarnings("javadoc")
    class Validator extends AbstractValidator<Time> {

        public Validator(Time annotation) {
            super(annotation);
        }

        @Override
        protected void validate(String name, String value, ErrorAppender appender) {
            if (value == null || value.isEmpty()) {
                return;
            }
            LocalDateTime t = LocalDateTime.parse(value);
            LocalDateTime now = LocalDateTime.now();
            if (now.plus(annotation.future(), annotation.unit()).isBefore(t) || now.minus(annotation.past(), annotation.unit()).isAfter(t)) {
                appender.addError(name, value, annotation.message(), "past", annotation.past(), "future", annotation.future(), "unit", annotation.unit());
            }
        }
    }
}
