package framework;

import static framework.TestBinder.User.Factory.F.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    Map<Class<?>, Class<?>> boxedMap = Tool
        .map(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class);

    /**
     * @param c class
     * @param value value
     * @return json
     */
    Object to(Class<?> c, Object value) {
        return c == null ? null
                : Try.s(() -> c.getMethod("valueOf", String.class)
                    .invoke(null, Objects.toString(value)), e -> {
                        Log.info(e::toString);
                        return value;
                    })
                    .get();
    }

    /**
     * @param c class
     * @param offset offset
     * @return max value(BigDecimal)
     */
    Object maxValue(Class<?> c, int offset) {
        try {
            return new BigDecimal(c.getField("MAX_VALUE")
                .get(null)
                .toString()).add(BigDecimal.valueOf(offset));
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new InternalError(e);
        }
    }

    @SuppressWarnings("javadoc")
    public static class User {
        int id;
        String name;
        LocalDate birthday;
        Gender gender;

        public enum Gender {
            MALE,
            FEMALE,
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof User && hashCode() == obj.hashCode();
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, birthday, gender);
        }
        
        @Override
        public String toString() {
            return String.format("id=%s, name=%s, birthday=%s, gender=%s", id, name, birthday, gender);
        }
        
        public User() {}

        public User(int id, String name, LocalDate birthday, Gender gender) {
            this.id = id;
            this.name = name;
            this.birthday = birthday;
            this.gender = gender;
        }

        public static class Factory extends AbstractBuilder<User, Factory, Factory.F> {
            enum F {
                id,
                name,
                birthday,
                gender
            };
        }
    }

    @SuppressWarnings("javadoc")
    public static class User2 {
        String name;
        int age;

        @Override
        public boolean equals(Object obj) {
            return obj instanceof User2 && hashCode() == obj.hashCode();
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
        
        @Override
        public String toString() {
            return String.format("name=%s, age=%s", name, age);
        }
    }

    {
        expect("null", n -> binder(null).bind(null, null)).toNull();
        expect("empty", n -> binder("").bind("", null)).toNull();

        for (Class<?> c : Tool
            .array(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class)) {
            Class<?> boxed = Tool.val(boxedMap.get(c), i -> i == null ? c : i);
            group(c.getSimpleName(), prefix -> {
                expect(prefix + ":1", n -> binder("a=1").bind("a", c)).toEqual(to(boxed, 1));
                expect(prefix + ":empty", n -> binder("a=").bind("a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":null", n -> binder("b=").bind("a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":a", n -> binder("a=a").bind("a", c)).toEqual(to(boxed, 0));
                expect(prefix + ":" + maxValue(boxed, 0), n -> binder("a=" + maxValue(boxed, 0)).bind("a", c)).toEqual(to(boxed, Try
                    .s(() -> boxed.getField("MAX_VALUE")
                        .get(null))
                    .get()));
                if (boxed == Float.class || boxed == Double.class) {
                    expect(prefix + ":" + maxValue(boxed, 1), n -> binder("a=" + maxValue(boxed, 1)).bind("a", c)).toEqual(to(boxed, Try
                        .s(() -> boxed.getField("MAX_VALUE")
                            .get(null))
                        .get()));
                } else {
                    expect(prefix + ":" + maxValue(boxed, 1), n -> binder("a=" + maxValue(boxed, 1)).bind("a", c)).toEqual(to(boxed, 0));
                }
            });
        }

        group("Tuple", prefix -> {
            expect(prefix + " a:int:1,b:int:2", n -> Tool.val(binder("a=1&b=2"), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                .toEqual(Tuple.of(1, 2));
            expect(prefix
                    + " a:int:empty,b:int:2", n -> Tool.val(binder("a=&b=2"), binder -> Tuple.of(binder.bind("a", int.class), binder.bind("b", int.class))))
                        .toEqual(Tuple.of(0, 2));
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
            expect(prefix + ":Optional:empty", n -> binder("s=").bind("s", Optional.class, String.class)).toEqual(Optional.empty());
            expect(prefix + ":Optional:null", n -> binder("a=def").bind("s", Optional.class, String.class)).toEqual(Optional.empty());
        });

        expect("String[]", n -> binder("a=abc&a=def").bind("a", String[].class)).toArrayEqual(Tool.array("abc", "def"));
        expect("int[]", n -> binder("a=1&a=2").bind("a", int[].class)).toArrayEqual(new int[] { 1, 2 });
        expect("List", n -> binder("a=abc&a=def").bind("a", List.class, String.class)).toEqual(Tool.list("abc", "def"));
        expect("EmptyList", n -> binder("a=").bind("a", List.class, String.class)).toEqual(Tool.list());
        expect("List<Long>", n -> binder("a=1234567890123&a=-222").bind("a", List.class, Long.class)).toEqual(Tool.list(1234567890123L, -222L));
        expect("Set", n -> binder("a=abc&a=def").bind("a", Set.class, String.class)).toEqual(Tool.set("abc", "def"));
        expect("Map", n -> binder("m.a=abc&m.b=def").bind("m", Map.class, String.class, String.class)).toEqual(Tool.map("a", "abc", "b", "def"));
        expect("Map<int>", n -> binder("m.a=1&m.b=2").bind("m", Map.class, String.class, Integer.class)).toEqual(Tool.map("a", 1, "b", 2));

        expect("LocalDate", n -> binder("a=2017-01-11").bind("a", LocalDate.class)).toEqual(LocalDate.of(2017, 1, 11));
//        expect("ClassFullArgsConstructor", n -> binder("c.id=1&c.name=abc&c.birthday=2001-02-03&c.gender=FEMALE").bind("c", User.class))
//            .toEqual(new User.Factory().set(id, 1, name, "abc", birthday, LocalDate.of(2001, 2, 3), gender, User.Gender.FEMALE)
//                .get());
//        expect("ClassNoConstructor", n -> binder("c.name=abc&c.age=22").bind("c", User2.class)).toEqual(Tool.peek(new User2(), u -> {
//            u.name = "abc";
//            u.age = 22;
//        }));
        expect("List<Map>", n -> binder("c[0].id=1&c[1].id=2&c[0].name=abc&c[1].name=def&c[0].birthday=2001-02-03&c[0].gender=FEMALE")
            .bind("c", List.class, Map.class))
                .toEqual(Tool.list(Tool.map("id", "1", "name", "abc", "birthday", "2001-02-03", "gender", "FEMALE"), Tool.map("id", "2", "name", "def")));
//        expect("List<Class>", n -> binder("c[0].id=1&c[0].name=abc&c[0].birthday=2001-02-03&c[0].gender=FEMALE").bind("c", List.class, User.class))
//            .toEqual(Tool.list(new User.Factory().set(id, 1)
//                .set(name, "abc")
//                .set(birthday, LocalDate.of(2001, 2, 3))
//                .set(gender, User.Gender.FEMALE)
//                .get()));
    }
    
    @SuppressWarnings("javadoc")
    public static void main(String... args) {
//        System.out.println(new Binder(new Parser.Url().parse("c[0].id=1&c[1].id=2&c[0].name=abc&c[1].name=def&c[0].birthday=2001-02-03&c[0].gender=FEMALE")).bind("c", List.class, Map.class));
//        System.out.println(new Binder(new Parser.Url().parse("m.a=abc&m.b=def")).bind("m", Map.class, String.class, String.class));
//        System.out.println(new Binder(new Parser.Url().parse("m.a=abc&m.b=def")).bind("m", Map.class, String.class, String.class));
        System.out.println(new Binder(new Parser.Url().parse("a=")).bind("a", List.class, String.class));
    }
}
