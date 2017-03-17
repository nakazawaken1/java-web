package framework;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            Class<?> boxed = Tool.val(boxedMap.get(c), i -> i == null ? c : i);
            groupWith(c.getSimpleName(), prefix -> {
                expect(prefix + ":1", from("a=1", "a", c)).toEqual(to(boxed, 1));
                expect(prefix + ":empty", from("a=", "a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":null", from("b=", "a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":a", from("a=a", "a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":" + maxValue(boxed, 0), from("a=" + maxValue(boxed, 0), "a", c))
                        .toEqual(to(boxed, Try.s(() -> boxed.getField("MAX_VALUE").get(null)).get()));
                if (boxed == Float.class || boxed == Double.class) {
                    expect(prefix + ":" + maxValue(boxed, 1), from("a=" + maxValue(boxed, 1), "a", c))
                            .toEqual(to(boxed, Try.s(() -> boxed.getField("MAX_VALUE").get(null)).get()));
                } else {
                    expect(prefix + ":" + maxValue(boxed, 1), from("a=" + maxValue(boxed, 1), "a", c)).toEqual(to(boxed, 0));
                }
            });
        }

        groupWith("Tuple", prefix -> {
            expect(prefix + " a:int:1,b:int:2",
                    () -> Tool.val(new Binder(new Parser.Url().parse("a=1&b=2")), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                            .toEqual(Tuple.of(1, 2));
            expect(prefix + " a:int:empty,b:int:2",
                    () -> Tool.val(new Binder(new Parser.Url().parse("a=&b=2")), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                            .toEqual(Tuple.of(0, 2));
            expect(prefix + " a:int:null,b:int:2",
                    () -> Tool.val(new Binder(new Parser.Url().parse("b=2")), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                            .toEqual(Tuple.of(0, 2));
        });

        groupWith("char", prefix -> {
            expect(prefix, () -> new Binder(new Parser.Url().parse("c=abc")).bind("c", char.class)).toEqual('a');
            expect(prefix + ":empty", () -> new Binder(new Parser.Url().parse("c=")).bind("c", char.class)).toEqual('\0');
            expect(prefix + ":null", () -> new Binder(new Parser.Url().parse("a=abc")).bind("c", char.class)).toEqual('\0');
            expect(prefix + ":Optional", () -> new Binder(new Parser.Url().parse("c=abc")).bind("c", Optional.class, char.class)).toEqual(Optional.of('a'));
            expect(prefix + ":Optional:empty", () -> new Binder(new Parser.Url().parse("c=")).bind("c", Optional.class, char.class)).toEqual(Optional.empty());
            expect(prefix + ":Optional:null", () -> new Binder(new Parser.Url().parse("a=abc")).bind("c", Optional.class, char.class))
                    .toEqual(Optional.empty());
        });
        groupWith("Character", prefix -> {
            expect(prefix, () -> new Binder(new Parser.Url().parse("c=abc")).bind("c", Character.class)).toEqual('a');
            expect(prefix + ":empty", () -> new Binder(new Parser.Url().parse("c=")).bind("c", Character.class)).toEqual('\0');
            expect(prefix + ":null", () -> new Binder(new Parser.Url().parse("a=abc")).bind("c", Character.class)).toEqual('\0');
            expect(prefix + ":Optional", () -> new Binder(new Parser.Url().parse("c=abc")).bind("c", Optional.class, Character.class))
                    .toEqual(Optional.of('a'));
            expect(prefix + ":Optional:empty", () -> new Binder(new Parser.Url().parse("c=")).bind("c", Optional.class, Character.class))
                    .toEqual(Optional.empty());
            expect(prefix + ":Optional:null", () -> new Binder(new Parser.Url().parse("a=abc")).bind("c", Optional.class, Character.class))
                    .toEqual(Optional.empty());
        });

        groupWith("String", prefix -> {
            expect(prefix, () -> new Binder(new Parser.Url().parse("s=abc")).bind("s", String.class)).toEqual("abc");
            expect(prefix + ":empty", () -> new Binder(new Parser.Url().parse("s=")).bind("s", String.class)).toEqual("");
            expect(prefix + ":null", () -> new Binder(new Parser.Url().parse("a=def")).bind("s", String.class)).toEqual("");
            expect(prefix + ":Optional", () -> new Binder(new Parser.Url().parse("s=abc")).bind("s", Optional.class, String.class)).toEqual(Optional.of("abc"));
            expect(prefix + ":Optional:empty", () -> new Binder(new Parser.Url().parse("s=")).bind("s", Optional.class, String.class)).toEqual(Optional.of(""));
            expect(prefix + ":Optional:null", () -> new Binder(new Parser.Url().parse("a=def")).bind("s", Optional.class, String.class))
                    .toEqual(Optional.empty());
        });

        expect("Array", () -> new Binder(new Parser.Url().parse("a=abc&a=def")).bind("a", String[].class)).toArrayEqual(Tool.array("abc", "def"));
    }
}
