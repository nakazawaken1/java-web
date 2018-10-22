package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.AbstractValidator;
import framework.annotation.Validator.ErrorAppender;

/**
 * Real number validation
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Validator(RegEx.Validator.class)
public @interface RegEx {
	/**
	 * @return Apply groups
	 */
	Class<? extends Valid.All>[] groups() default Valid.All.class;

	/**
	 * @return Regular expression
	 */
	String value();
	
	/**
	 * @return Error message
	 */
	String message() default "{App.Alert.real}";

	@SuppressWarnings("javadoc")
	class Validator extends AbstractValidator<RegEx> {
		public Validator(RegEx annotation) {
			super(annotation);
		}

		@Override
		protected void validate(String name, String value, ErrorAppender appender) {
			if (value != null && !value.matches(annotation.value())) {
				appender.addError(name, value, annotation.message(), "value", annotation.value());
			}
		}
	}
}
