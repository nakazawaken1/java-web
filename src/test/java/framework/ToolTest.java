package framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool test
 */
public class ToolTest extends Tester {
    {
        group("add", () -> {
            Map<String, List<String>> map = new HashMap<>();
            expect("1", () -> Tool.json(Tool.add(map, "key", "value1").get("key"))).toEqual(Tool.json(Tool.array("value1")));
            expect("2", () -> Tool.json(Tool.add(map, "key", "value2").get("key"))).toEqual(Tool.json(Tool.array("value1", "value2")));
        });

        group("trim", () -> {
            String text = ";;;abc;;;";
            expect("null", () -> Tool.trim(null, null, null)).toNull();
            expect("both", () -> Tool.trim(";", text, ";")).toEqual("abc");
            expect("no match", () -> Tool.trim(":", text, ":")).toEqual(text);
            expect("left null", () -> Tool.trim(null, text, ";")).toEqual(";;;abc");
            expect("left empty", () -> Tool.trim("", text, ";")).toEqual(";;;abc");
            expect("all left", () -> Tool.trim(";", ";;;;", ":")).toEqual("");
            expect("right null", () -> Tool.trim(";", text, null)).toEqual("abc;;;");
            expect("rihgt empty", () -> Tool.trim(";", text, "")).toEqual("abc;;;");
            expect("all right", () -> Tool.trim(":", ";;;;", ";")).toEqual("");
            expect("all left, right", () -> Tool.trim(";", ";;;;", ";")).toEqual("");
            expect("spaces", () -> Tool.trim(" 　", "　 　あ　 いうえお 　 ", " 　")).toEqual("あ　 いうえお");
        });
    }
}
