package framework.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import framework.AbstractValidator;
import framework.Formatter;
import framework.Reflector;
import framework.Session;
import framework.Tool;
import framework.Try;
import framework.Tuple;
import framework.annotation.Valid.All;

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
	class Manager {
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
		
		/**
		 * @param valid      Valid
		 * @param clazz      Target class
		 * @param parameterName ParameterName
		 * @param parameters Target values
		 * @param errors     Errors
		 * @param parameterizedType Generic type
		 */
		public static void validateClass(Class<? extends All> valid, Class<?> clazz, String parameterName, Map<String, List<String>> parameters, ErrorAppender errors, Type... parameterizedType) {
			if(Iterable.class.isAssignableFrom(clazz)) {
				AtomicInteger index = new AtomicInteger();
				parameters.entrySet().stream()//
					.filter(e -> e.getKey().startsWith(parameterName))//match prefix
					.flatMap(e -> {//parse index, rest prefix, and value
						String key = e.getKey();
						int begin = key.indexOf('[');
						int end = key.indexOf(']', begin + 1);
						if(begin < end) {
							return Stream.of(Tuple.of(Tool.integer(key.substring(begin + 1, end)).orElseGet(index::getAndIncrement), key, e.getValue()));
						}
						return e.getValue().stream().map(v -> Tuple.of(index.getAndIncrement(), "", Arrays.asList(v)));
					}).collect(Collectors.groupingBy(t -> t.l))//grouping
					.values().stream().sorted(Comparator.comparing(t -> t.get(0).l)).forEach(t -> {
						validateClass(valid, (Class<?>)parameterizedType[0], parameterName + '[' + t.get(0).l + ']', t.stream().collect(Collectors.toMap(u -> u.r.l, u -> u.r.r)), errors);
					});
				return;
			}
			Reflector.fields(clazz).forEach((name, field) -> {
				Stream.of(field.getAnnotations())//
					.map(a -> Tuple.of(a, a.annotationType().getAnnotation(Validator.class)))//
					.filter(t -> t.r != null)//
					.forEach(t -> instance(t.l).ifPresent(v -> {
						String fullName = parameterName + "." + name;
						String value = Tool.getJoin(parameters, fullName, ",").orElse(null);
						v.validate(valid, fullName, value, errors);
					}));
			});
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
	            .format(error, Formatter::excludeForHtml, Tool::htmlEscape, Session.currentLocale(), Tool.map("validatedValue", value, keyValues)), ArrayList::new);
	    }

		/**
		 * @param changer Key converter
		 * @return Changed Errors
		 */
		public Errors changeKeys(Function<String, String> changer) {
			return entrySet().stream().collect(Errors::new, (m, e) -> m.put(changer.apply(e.getKey()), e.getValue()), Map::putAll);
		}
	}
}
