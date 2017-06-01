package framework;

/**
 * Parser test
 */
public class TestTraverser extends Tester {
    @SuppressWarnings("javadoc")
    static class O {
        final int key;
        final String value;

        O(int key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    {
        group("Traverser", g -> {
            expect(g + ":csv:null", n -> Tool.csv(null)).toEqual("");
            expect(g + ":csv:empty", n -> Tool.csv("")).toEqual("");
            expect(g + ":csv:1", n -> Tool.csv(1)).toEqual("1");
            expect(g + ":csv:[1,2,3]", n -> Tool.csv(Tool.array(1, 2, 3))).toEqual("\"1\",\"2\",\"3\"", "");
            expect(g + ":csv:{a: 1, b: 2}", n -> Tool.csv(Tool.map("a", 1, "b", 2))).toEqual("\"a\",\"b\"", "\"1\",\"2\"", "");
            expect(g + ":csv:[{a: 1, b: 2}, {a: 3, b: 4}]", n -> Tool.csv(Tool.array(Tool.map("a", 1, "b", 2), Tool.map("a", 3, "b", 4))))
                    .toEqual("\"a\",\"b\"", "\"1\",\"2\"", "\"3\",\"4\"", "");
            expect(g + ":csv:object", n -> Tool.csv(Tool.array(new O(1, "a"), new O(2, "b")))).toEqual("\"key\",\"value\"", "\"1\",\"a\"", "\"2\",\"b\"", "");
        });
    }
}
