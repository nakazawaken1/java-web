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
            expect("1", () -> {Tool.add(map, "key", "value1"); return Tool.json(map.get("key"));}).toEqual(Tool.json(Tool.array("value1")));
            expect("2", () -> {Tool.add(map, "key", "value2"); return Tool.json(map.get("key"));}).toEqual(Tool.json(Tool.array("value1", "value2")));
        });

        group("trim", () -> {
            String text = ";;;abc;;;";
            expect("null", () -> Tool.trim(null, null, null)).toNull();
            expect("both", () -> Tool.trim(";", text, ";")).toEqual("abc");
            expect("left null", () -> Tool.trim(null, text, ";")).toEqual(";;;abc");
            expect("left empty", () -> Tool.trim("", text, ";")).toEqual(";;;abc");
        });
    }
}
