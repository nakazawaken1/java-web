package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * Class or field Name mapping
 */
@Target({ ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Mapping {

    /**
     * @return mapping name(use field name if empty)
     */
    String value() default "";

    /**
     * @return Mapping function
     */
    Class<? extends Mapper> mapper() default Mapper.class;

    /**
     * Mapper logic
     */
    static class Mapper {
	/**
	 * @param clazz Target class
	 * @param field Target field
	 * @param value Value parameter
	 * @return Mapped name
	 */
	public String map(Class<?> clazz, Field field, String value) {
	    return value == null || value.isEmpty() ? field != null ? field.getName() : clazz.getSimpleName() : value;
	}
    }

    /**
     * Snake case mapper
     */
    static class ToSnake extends Mapper {

	/**
	 * Case converter
	 */
	protected final Function<Character, Character> caser;

	/**
	 * Constructor
	 */
	public ToSnake() {
	    this.caser = c -> c;
	}

	/**
	 * @param caser Case converter
	 */
	public ToSnake(Function<Character, Character> caser) {
	    this.caser = caser;
	}

	@Override
	public String map(Class<?> clazz, Field field, String value) {
	    return value == null || value.isEmpty() ? to(field != null ? field.getName() : clazz.getSimpleName())
		    : value;
	}

	/**
	 * @param name Name
	 * @return Converted value
	 */
	protected String to(String name) {
	    StringBuilder snake = new StringBuilder(name.length());
	    for (char c : name.toCharArray()) {
		if (Character.isUpperCase(c)) {
		    snake.append('_');
		}
		snake.append(caser.apply(c));
	    }
	    return snake.charAt(0) == '_' ? snake.substring(1) : snake.toString();
	}
    }

    /**
     * Upper snake case mapper
     */
    static class ToSnakeUpper extends ToSnake {
	/**
	 * Constructor
	 */
	public ToSnakeUpper() {
	    super(Character::toUpperCase);
	}
    }

    /**
     * Lower snake case mapper
     */
    static class ToSnakeLower extends ToSnake {
	/**
	 * Constructor
	 */
	public ToSnakeLower() {
	    super(Character::toLowerCase);
	}
    }
}
