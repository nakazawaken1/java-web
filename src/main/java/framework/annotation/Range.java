package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import framework.AbstractValidator;

/**
 * value range limitation
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default {};

    /**
     * @return minimum number
     */
    double min() default Double.NEGATIVE_INFINITY;

    /**
     * @return maximum number
     */
    double value() default Double.POSITIVE_INFINITY;

    /**
     * @return minimum length of integer part
     */
    int integerMin() default 0;

    /**
     * @return maximum length of integer part
     */
    int integerMax() default Integer.MAX_VALUE;

    /**
     * @return minimum length of fraction part
     */
    int fractionMin() default 0;

    /**
     * @return maximum length of fraction part
     */
    int fractionMax() default Integer.MAX_VALUE;

    /**
     * @return Error message
     */
    String message() default "{Sys.Alert.range}";

    @SuppressWarnings("javadoc")
    class Validator extends AbstractValidator<Range> {

        public Validator(Range annotation) {
            super(annotation);
        }

        @Override
        protected void validate(String name, String value, ErrorAppender appender) {
            if (value == null || value.isEmpty()) {
                return;
            }
            BigDecimal n = new BigDecimal(value);
            boolean isError = false;
            if ((annotation.min() != Double.NEGATIVE_INFINITY && n.compareTo(BigDecimal.valueOf(annotation.min())) < 0)
                    || (annotation.value() != Double.POSITIVE_INFINITY && BigDecimal.valueOf(annotation.value()).compareTo(n) < 0)) {
                isError = true;
            } else {
                int integer = value.indexOf(".");
                if (integer < 0) {
                    integer = value.length();
                }
                int fraction = Math.max(0, value.length() - integer - 1);
                if (integer < annotation.integerMin() || annotation.integerMax() < integer) {
                    isError = true;
                } else if (fraction < annotation.fractionMin() || annotation.fractionMax() < fraction) {
                    isError = true;
                }
            }
            if (isError) {
                appender.addError(name, value, annotation.message(), "min", annotation.min(), "value", annotation.value(), "integerMin",
                        annotation.integerMin(), "integerMax", annotation.integerMax(), "fractionMin", annotation.fractionMin(), "fractionMax",
                        annotation.fractionMax());
            }
        }
    }
}
