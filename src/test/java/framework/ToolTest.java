package framework;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

        group("nextMillis", () -> {
            ZonedDateTime now = ZonedDateTime.now();
            Function<String, Object> from = s -> Tool.nextMillis(s, now);
            Function<Function<ZonedDateTime, ZonedDateTime>, Long> to = d -> Duration.between(now, d.apply(now)).toMillis(); 
            expectWith("1D", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.DAYS)));
            expectWith("1H", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.HOURS)));
            expectWith("1M", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.MINUTES)));
            expectWith("1S", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.SECONDS)));
            expectWith("2", from).toEqual(to.apply(d -> d.plusMonths(1).withDayOfMonth(2).truncatedTo(ChronoUnit.DAYS)));
            expectWith("12:00", from).toEqual(to.apply(d -> d.plusDays(1).withHour(12).truncatedTo(ChronoUnit.HOURS)));
            expectWith("12:34", from).toEqual(to.apply(d -> d.plusDays(1).withHour(12).withMinute(34).truncatedTo(ChronoUnit.MINUTES)));
            expectWith(":01", from).toEqual(to.apply(d -> d.plusHours(1).withMinute(1).truncatedTo(ChronoUnit.MINUTES)));
            expectWith("::02", from).toEqual(to.apply(d -> d.plusMinutes(1).withSecond(2).truncatedTo(ChronoUnit.SECONDS)));
            expectWith("12:34:56", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.HOURS)));
            expectWith("Fri 12:34", from).toEqual(Duration.between(now, now.plus(1, ChronoUnit.HOURS)));
            expectWith("3 10:00", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.HOURS)));
            expectWith("2/3 10:00", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.HOURS)));
        });
    }
}
