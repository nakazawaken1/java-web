package framework;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Getter
 */
public class Getters {

    /**
     * getters
     */
    Map<String, Method> getters;
    
    /**
     * @param clas class
     */
    public Getters(Class<?> clas) {
        getters = Stream.of(clas.getMethods()).filter(
                m -> (m.getName().startsWith("get") || m.getName().startsWith("is")) && m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers()))
                .collect(Collectors.toMap(Method::getName, m -> m));
    }

    /**
     * @param owner owner
     * @param name name
     * @return value
     */
    public Optional<Object> get(Object owner, String name) {
        Method method = getters.get("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (method == null && name.startsWith("is")) {
            method = getters.get(name);
        }
        return Optional.ofNullable(method).map(Try.f(m -> m.invoke(owner)));
    }

}
