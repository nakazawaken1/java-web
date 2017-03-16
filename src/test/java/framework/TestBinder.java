package framework;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Binder test
 */
public class TestBinder extends Tester {
    /**
     * logger
     */
    Logger logger = Tool.getLogger();

    /**
     * @param text request
     * @param name name
     * @param clazz class
     * @return json
     */
    Supplier<Object> from(String text, String name, Class<?> clazz) {
        return () -> new Binder(new Parser.Url().parse(text)).bind(name, clazz);
    }

    /**
     * class map
     */
    Map<Class<?>, Class<?>> boxedMap = Tool.map(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class, float.class,
            Float.class, double.class, Double.class);

    /**
     * @param c class
     * @param value value
     * @return json
     */
    Object to(Class<?> c, Object value) {
        return c == null ? null : Try.s(() -> c.getMethod("valueOf", String.class).invoke(null, Objects.toString(value)), e -> {
            logger.info(e.toString());
            return value;
        }).get();
    }

    /**
     * @param c class
     * @param offset offset
     * @return max value(BigDecimal)
     */
    Object maxValue(Class<?> c, int offset) {
        try {
            return new BigDecimal(c.getField("MAX_VALUE").get(null).toString()).add(BigDecimal.valueOf(offset));
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new InternalError(e);
        }
    }

    {
        expect("null", from(null, null, null)).toEqual(to(null, null));
        expect("empty", from("", "", null)).toEqual(to(null, null));

        for (Class<?> c : Tool.array(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class, float.class,
                Float.class, double.class, Double.class)) {
            Class<?> boxed = Tool.val(boxedMap.get(c), i -> i, () -> c);
            expect(c + ":1", from("a=1", "a", c)).toEqual(to(boxed, 1));
            expect(c + ":", from("a=", "a", c)).toEqual(to(boxed, 0));
            expect(c + ":a", from("a=a", "a", c)).toEqual(to(boxed, 0));
            expect(c + ":" + maxValue(boxed, 0), from("a=" + maxValue(boxed, 0), "a", c))
                    .toEqual(to(boxed, Try.s(() -> boxed.getField("MAX_VALUE").get(null)).get()));
            if (boxed == Float.class || boxed == Double.class) {
                expect(c + ":" + maxValue(boxed, 1), from("a=" + maxValue(boxed, 1), "a", c))
                        .toEqual(to(boxed, Try.s(() -> boxed.getField("MAX_VALUE").get(null)).get()));
            } else {
                expect(c + ":" + maxValue(boxed, 1), from("a=" + maxValue(boxed, 1), "a", c)).toEqual(to(boxed, 0));
            }
        }

        {
            Binder binder = new Binder(new Parser.Url().parse("a=1&b=2"));
            expect("a,b", () -> Tool.map("a", binder.bind("a", int.class), "b", binder.bind("b", int.class))).toEqual(Tool.map("a", 1, "b", 2));
        }

        {
            Binder binder = new Binder(new Parser.Url().parse("c=abc"));
            Object result = Tool.map("c", 'a');
            expect("char", () -> Tool.map("c", binder.bind("c", char.class))).toEqual(result);
            expect("Character", () -> Tool.map("c", binder.bind("c", Character.class))).toEqual(result);
        }

        {
            Binder binder = new Binder(new Parser.Url().parse("s=abc"));
            Object result = Tool.map("s", "abc");
            expect("string", () -> Tool.map("s", binder.bind("s", String.class))).toEqual(result);
        }

        expect("array", () -> new Binder(new Parser.Url().parse("a=abc&a=def")).bind("a", String[].class)).toArrayEqual(Tool.array("abc", "def"));
    }
}
