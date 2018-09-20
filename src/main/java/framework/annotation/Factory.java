package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.AbstractBuilder;
import framework.AbstractBuilder.PropertyBuilder;
import framework.Reflector;

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
			Factory factory = targetClass.getAnnotation(Factory.class);
			Class<?> builder = factory != null ? factory.value() : Reflector.clazz(targetClass.getName() + "$Builder").orElse(null);
			return builder != null ? (AbstractBuilder<T, ?, ?>) Reflector.instance(builder) : new PropertyBuilder<>(() -> Reflector.instance(targetClass));
		}
	}
}
