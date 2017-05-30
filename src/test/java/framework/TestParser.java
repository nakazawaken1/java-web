package framework;

import java.util.function.Function;

/**
 * Parser test
 */
public class TestParser extends Tester {
    /**
     * from
     */
    Function<String, Object> from = s -> Tool.json(new Parser.Url().parse(s.substring("Parser:".length())));

    /**
     * @param values values
     * @return json
     */
    Object to(Object... values) {
        return Tool.json(Tool.map(values));
    }

    {
        group("Parser", g -> {
            expect(g + ":null", n -> Tool.json(new Parser.Url().parse(null))).toEqual(to());
            expect(g + ":empty", n -> Tool.json(new Parser.Url().parse(""))).toEqual(to());
            expect(g + ":a=1", from).toEqual(to("a", Tool.list("1")));
            expect(g + ":a=1&a=2", from).toEqual(to("a", Tool.list("1", "2")));
            expect(g + ":a=&a=2", from).toEqual(to("a", Tool.list("", "2")));
            expect(g + ":a=&a=2=3", from).toEqual(to("a", Tool.list("", "2=3")));
            expect(g + ":a=1%262", from).toEqual(to("a", Tool.list("1&2")));
        });
    }
}
