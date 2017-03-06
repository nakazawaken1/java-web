package framework;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

import framework.annotation.Required;

/**
 * value object builder
 * @param <VALUE> value type
 * @param <BUILDER> builder type
 */
public abstract class AbstractBuilder<VALUE, BUILDER extends AbstractBuilder<VALUE, BUILDER>> implements Supplier<VALUE> {
    /**
     * target fields
     */
    Enum<?>[] fields;
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
        this.fields = eClass.getEnumConstants();
        this.clazz = clazz;
        this.values = new Object[fields.length];
        this.sets = new boolean[fields.length];
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
     * @param value source object
     * @return builder
     */
    @SuppressWarnings("unchecked")
    public BUILDER copy(VALUE value) {
        for (Enum<?> i : fields) {
            try {
                set(i, clazz.getField(i.name()).get(value));
            } catch (IllegalArgumentException | IllegalAccessException | SecurityException | NoSuchFieldException e) {
                throw new InternalError(e);
            }
        }
        return (BUILDER) this;
    }

    /* (non-Javadoc)
     * @see java.util.function.Supplier#get()
     */
    @SuppressWarnings("unchecked")
    @Override
    public VALUE get() {
        for (Enum<?> i : fields) {
            try {
                if (clazz.getField(i.name()).getAnnotation(Required.class) != null && !sets[i.ordinal()]) {
                    throw new IllegalArgumentException(clazz.getName() + '.' + i.name() + " mast set a value.");
                }
            } catch (NoSuchFieldException | SecurityException e) {
                throw new InternalError(e);
            }
        }
        try {
            return (VALUE) clazz.getDeclaredConstructors()[0].newInstance(values);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
            throw new InternalError(e);
        }
    }
}
