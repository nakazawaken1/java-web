package framework;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import framework.annotation.Valid;

/**
 * Annotation validator base
 *
 * @param <T> Annotation type
 */
public abstract class AbstractValidator<T extends Annotation> {
    /**
     * Annotation
     */
    protected T annotation;

    /**
     * @param annotation Annotation
     */
    public AbstractValidator(T annotation) {
        this.annotation = annotation;
    }

    /**
     * @param name Target name
     * @param value Target value
     * @param appender Error appender
     */
    protected abstract void validate(String name, String value, ErrorAppender appender);

    /**
     * @param valid Valid
     * @param name Target name
     * @param value Target value
     * @param appender Error appender
     */
    final public void validate(Class<? extends Valid.All> valid, String name, String value, ErrorAppender appender) {
        if (Tool.or(Reflector.method(annotation.getClass(), "groups"), () -> Reflector.method(annotation.getClass(), "value")).map(Try.f(method -> (Class[]) method.invoke(annotation)))
                .flatMap(cs -> Stream.of(cs).filter(c -> valid.isAssignableFrom(c)).findAny()).isPresent()) {
            validate(name, value, appender);
        }
    }

    /**
     * Error appender
     */
    public static interface ErrorAppender {
        /**
         * @param name Name
         * @param value Value
         * @param error Error
         * @param keyValues Parameters
         */
        void addError(String name, String value, String error, Object... keyValues);
    }
}
