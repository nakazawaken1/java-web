package framework;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import framework.annotation.Required;

/**
 * value object builder
 * 
 * @param <VALUE> value type
 * @param <BUILDER> builder type
 */
public abstract class AbstractBuilder<VALUE, BUILDER extends AbstractBuilder<VALUE, BUILDER>> implements Supplier<VALUE> {
    /**
     * target field names
     */
    Enum<?>[] names;
    /**
     * target fields
     */
    Field[] fields;
    /**
     * target class
     */
    Class<VALUE> clazz;
    /**
     * values
     */
    Object[] values;
    /**
     * set values
     */
    boolean[] sets;

    /**
     * @param eClass fields class
     * @param clazz target class
     */
    public AbstractBuilder(Class<? extends Enum<?>> eClass, Class<VALUE> clazz) {
        this.names = eClass.getEnumConstants();
        this.fields = Stream.of(names).map(i -> Reflector.field(clazz, i.name())).filter(Optional::isPresent).map(Optional::get).toArray(Field[]::new);
        this.clazz = clazz;
        this.values = new Object[names.length];
        this.sets = new boolean[names.length];
    }

    /**
     * @param en field
     * @param value value
     * @return builder
     */
    @SuppressWarnings("unchecked")
    public BUILDER set(Enum<?> en, Object value) {
        int i = en.ordinal();
        values[i] = value;
        sets[i] = true;
        return (BUILDER) this;
    }

    /**
     * @param name field
     * @param value value
     * @return builder
     */
    @SuppressWarnings("unchecked")
    public BUILDER set(String name, Object value) {
        Stream.of(names).filter(i -> i.name().equals(name)).findFirst().map(Enum::ordinal).ifPresent(i -> {
            values[i] = value;
            sets[i] = true;
        });
        return (BUILDER) this;
    }

    /**
     * @param value source object
     * @return builder
     */
    @SuppressWarnings("unchecked")
    public BUILDER copy(VALUE value) {
        for (Enum<?> i : names) {
            try {
                set(i, fields[i.ordinal()].get(value));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        }
        return (BUILDER) this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.function.Supplier#get()
     */
    @Override
    public VALUE get() {
        for (Enum<?> i : names) {
            if (Optional.ofNullable(fields[i.ordinal()].getAnnotation(Required.class)).filter(a -> a.value().length <= 0).isPresent() && !sets[i.ordinal()]) {
                throw new IllegalArgumentException(clazz.getName() + '.' + i.name() + " mast set a value.");
            }
        }
        try {
            return (VALUE) Reflector.constructor(clazz, Stream.of(fields).map(Field::getType).toArray(Class[]::new)).orElseThrow(IllegalArgumentException::new)
                    .newInstance(values);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new InternalError(e);
        }
    }
}
