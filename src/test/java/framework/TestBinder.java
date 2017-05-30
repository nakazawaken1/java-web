package framework;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Binder test
 */
public class TestBinder extends Tester {

    /**
     * @param text text
     * @return binder
     */
    static Binder binder(String text) {
        return new Binder(new Parser.Url().parse(text));
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
            Log.info(e::toString);
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
        expect("null", n -> binder(null).bind(null, null)).toNull();
        expect("empty", n -> binder("").bind("", null)).toNull();

        for (Class<?> c : Tool.array(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class, float.class,
                Float.class, double.class, Double.class)) {
            Class<?> boxed = Tool.val(boxedMap.get(c), i -> i == null ? c : i);
            group(c.getSimpleName(), prefix -> {
                expect(prefix + ":1", n -> binder("a=1").bind("a", c)).toEqual(to(boxed, 1));
                expect(prefix + ":empty", n -> binder("a=").bind("a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":null", n -> binder("b=").bind("a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":a", n -> binder("a=a").bind("a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":" + maxValue(boxed, 0), n -> binder("a=" + maxValue(boxed, 0)).bind("a", c))
                        .toEqual(to(boxed, Try.s(() -> boxed.getField("MAX_VALUE").get(null)).get()));
                if (boxed == Float.class || boxed == Double.class) {
                    expect(prefix + ":" + maxValue(boxed, 1), n -> binder("a=" + maxValue(boxed, 1)).bind("a", c))
                            .toEqual(to(boxed, Try.s(() -> boxed.getField("MAX_VALUE").get(null)).get()));
                } else {
                    expect(prefix + ":" + maxValue(boxed, 1), n -> binder("a=" + maxValue(boxed, 1)).bind("a", c)).toEqual(to(boxed, 0));
                }
            });
        }

        group("Tuple", prefix -> {
            expect(prefix + " a:int:1,b:int:2", n -> Tool.val(binder("a=1&b=2"), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                    .toEqual(Tuple.of(1, 2));
            expect(prefix + " a:int:empty,b:int:2",
                    n -> Tool.val(binder("a=&b=2"), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class)))).toEqual(Tuple.of(0, 2));
            expect(prefix + " a:int:null,b:int:2", n -> Tool.val(binder("b=2"), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                    .toEqual(Tuple.of(0, 2));
        });

        group("char", prefix -> {
            expect(prefix, n -> binder("c=abc").bind("c", char.class)).toEqual('a');
            expect(prefix + ":empty", n -> binder("c=").bind("c", char.class)).toEqual('\0');
            expect(prefix + ":null", n -> binder("a=abc").bind("c", char.class)).toEqual('\0');
            expect(prefix + ":Optional", n -> binder("c=abc").bind("c", Optional.class, char.class)).toEqual(Optional.of('a'));
            expect(prefix + ":Optional:empty", n -> binder("c=").bind("c", Optional.class, char.class)).toEqual(Optional.empty());
            expect(prefix + ":Optional:null", n -> binder("a=abc").bind("c", Optional.class, char.class)).toEqual(Optional.empty());
        });
        group("Character", prefix -> {
            expect(prefix, n -> binder("c=abc").bind("c", Character.class)).toEqual('a');
            expect(prefix + ":empty", n -> binder("c=").bind("c", Character.class)).toEqual('\0');
            expect(prefix + ":null", n -> binder("a=abc").bind("c", Character.class)).toEqual('\0');
            expect(prefix + ":Optional", n -> binder("c=abc").bind("c", Optional.class, Character.class)).toEqual(Optional.of('a'));
            expect(prefix + ":Optional:empty", n -> binder("c=").bind("c", Optional.class, Character.class)).toEqual(Optional.empty());
            expect(prefix + ":Optional:null", n -> binder("a=abc").bind("c", Optional.class, Character.class)).toEqual(Optional.empty());
        });

        group("String", prefix -> {
            expect(prefix, n -> binder("s=abc").bind("s", String.class)).toEqual("abc");
            expect(prefix + ":empty", n -> binder("s=").bind("s", String.class)).toEqual("");
            expect(prefix + ":null", n -> binder("a=def").bind("s", String.class)).toEqual("");
            expect(prefix + ":Optional", n -> binder("s=abc").bind("s", Optional.class, String.class)).toEqual(Optional.of("abc"));
            expect(prefix + ":Optional:empty", n -> binder("s=").bind("s", Optional.class, String.class)).toEqual(Optional.of(""));
            expect(prefix + ":Optional:null", n -> binder("a=def").bind("s", Optional.class, String.class)).toEqual(Optional.empty());
        });

        expect("Array", n -> binder("a=abc&a=def").bind("a", String[].class)).toArrayEqual(Tool.array("abc", "def"));
    }
}
