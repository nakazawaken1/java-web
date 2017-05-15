package framework;

import java.text.MessageFormat;
import java.util.Locale;

import framework.annotation.Config;

/**
 * Message for enum
 */
public interface Message {
    /**
     * @return message
     */
    default String defaultMessage() {
        return Reflector.field(getClass(), ((Enum<?>) this).name()).map(Reflector::mappingFieldName).orElse(null);
    }

    /**
     * @return message
     */
    default String message() {
        return message(Session.currentLocale());
    }

    /**
     * @param locale locale
     * @return message
     */
    default String message(Locale locale) {
        Class<?> clazz = getClass();
        String name = getClass().getSimpleName();
        do {
            clazz = clazz.getDeclaringClass();
            name = clazz.getSimpleName() + "." + name;
        } while (clazz.getAnnotation(Config.class) == null);
        return Config.Injector.getSource(clazz, locale).getProperty(name + "." + ((Enum<?>) this).name());
    }

    /**
     * @param locale Locale
     * @param values Values
     * @return Message
     */
    default String format(Locale locale, Object... values) {
        return MessageFormat.format(message(locale), values);
    }
}
