package framework.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import framework.AbstractValidator;
import framework.Formatter;
import framework.Reflector;
import framework.Session;
import framework.Tool;
import framework.Try;

/**
 * Validator info
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Validator {
	/**
	 * @return Validator implementation
	 */
	Class<? extends AbstractValidator<?>> value();

	/**
	 * Factory constructor
	 */
	class Constructor {
		/**
		 * @param annotation Target annotation
		 * @return Validator instance
		 */
		public static Optional<AbstractValidator<?>> instance(Annotation annotation) {
			try {
				Class<?> clazz = annotation.annotationType();
				return Reflector.constructor(clazz.getAnnotation(Validator.class).value(), clazz)
						.map(Try.f(i -> i.newInstance(annotation)));
			} catch (RuntimeException e) {
				return Optional.empty();
			}
		}
	}

    /**
     * Error appender
     */
    interface ErrorAppender {
        /**
         * @param name Name
         * @param value Value
         * @param error Error
         * @param keyValues Parameters
         */
        void addError(String name, String value, String error, Object... keyValues);
    }
	
	/**
	 * Errors(name, errorMessages)
	 */
	@SuppressWarnings("serial")
	class Errors extends LinkedHashMap<String, List<String>> implements ErrorAppender {

	    /*
	     * (non-Javadoc)
	     * 
	     * @see framework.AbstractValidator.ErrorAppender#addError(java.lang.String, java.lang.String, java.lang.String, java.lang.Object[])
	     */
	    @Override
	    public void addError(String name, String value, String error, Object... keyValues) {
	        Tool.addValue(this, name, Formatter
	            .format(error, Formatter::excludeForHtml, Tool::htmlEscape, Session.currentLocale(), Tool.map("validatedValue", value, keyValues)));
	    }
	}
}
