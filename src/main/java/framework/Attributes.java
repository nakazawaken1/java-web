package framework;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * session scoped object
 * @param <ValueType> value type
 */
public interface Attributes<ValueType> extends Map<String, ValueType> {
    /**
     * @return attribute names
     */
    Stream<String> names();

    /**
     * @param name attribute name
     * @return attribute value
     */
    <T extends ValueType> Optional<T> getAttr(String name);

    /**
     * @param name attribute name
     * @param value attribute value
     */
    void setAttr(String name, ValueType value);

    /**
     * @param name attribute name
     */
    void removeAttr(String name);

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#size()
     */
    @Override
    default int size() {
        return (int) names().count();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#isEmpty()
     */
    @Override
    default boolean isEmpty() {
        return !names().findAny().isPresent();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    @Override
    default boolean containsKey(Object key) {
        return names().anyMatch(key::equals);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    @Override
    default boolean containsValue(Object value) {
        return values().contains(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#get(java.lang.Object)
     */
    @Override
    default ValueType get(Object key) {
        return getAttr((String) key).orElse(null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    @Override
    default ValueType put(String key, ValueType value) {
        ValueType old = getAttr(key).orElse(null);
        setAttr(key, value);
        return old;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#remove(java.lang.Object)
     */
    @Override
    default ValueType remove(Object key) {
        ValueType old = getAttr((String) key).orElse(null);
        removeAttr((String) key);
        return old;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#putAll(java.util.Map)
     */
    @Override
    default void putAll(Map<? extends String, ? extends ValueType> m) {
        m.forEach((k, v) -> setAttr(k, v));
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#clear()
     */
    @Override
    default void clear() {
        names().forEach(this::removeAttr);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#keySet()
     */
    @Override
    default Set<String> keySet() {
        return names().collect(Collectors.toSet());
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#values()
     */
    @Override
    default Collection<ValueType> values() {
        return names().map(i -> getAttr(i).orElse(null)).collect(Collectors.toList());
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Map#entrySet()
     */
    @Override
    default Set<java.util.Map.Entry<String, ValueType>> entrySet() {
        return names().map(i -> Tool.pair(i, getAttr(i).orElse(null))).collect(Collectors.toSet());
    }

}
