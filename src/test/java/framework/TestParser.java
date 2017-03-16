package framework;

import java.util.Arrays;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Parser test
 */
public class TestParser extends Tester {
    /**
     * logger
     */
    Logger logger = Tool.getLogger();
    /**
     * from
     */
    Function<String, Object> from = s -> Tool.json(new Parser.Url().parse(s));

    /**
     * @param values values
     * @return json
     */
    Object to(Object... values) {
        return Tool.json(Tool.jsonMap(values));
    }

    {
        expectWith(null, from).toEqual(to());
        expectWith("", from).toEqual(to());
        expectWith("a=1", from).toEqual(to("a", Arrays.asList("1")));
        expectWith("a=1&a=2", from).toEqual(to("a", Arrays.asList("1", "2")));
        expectWith("a=&a=2", from).toEqual(to("a", Arrays.asList("", "2")));
        expectWith("a=&a=2=3", from).toEqual(to("a", Arrays.asList("", "2=3")));
        expectWith("a=1%262", from).toEqual(to("a", Arrays.asList("1&2")));
    }
}
