package framework;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
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
        groupWith("add", g -> {
            Map<String, List<String>> map = new HashMap<>();
            expect(g + ":1", () -> Tool.json(Tool.addValue(map, "key", "value1"))).toEqual(Tool.json(Tool.array("value1")));
            expect(g + ":2", () -> Tool.json(Tool.addValue(map, "key", "value2"))).toEqual(Tool.json(Tool.array("value1", "value2")));
        });

        groupWith("trim", g -> {
            String text = ";;;abc;;;";
            expect(g + ":null", () -> Tool.trim(null, null, null)).toNull();
            expect(g + ":both", () -> Tool.trim(";", text, ";")).toEqual("abc");
            expect(g + ":no match", () -> Tool.trim(":", text, ":")).toEqual(text);
            expect(g + ":left null", () -> Tool.trim(null, text, ";")).toEqual(";;;abc");
            expect(g + ":left empty", () -> Tool.trim("", text, ";")).toEqual(";;;abc");
            expect(g + ":all left", () -> Tool.trim(";", ";;;;", ":")).toEqual("");
            expect(g + ":right null", () -> Tool.trim(";", text, null)).toEqual("abc;;;");
            expect(g + ":rihgt empty", () -> Tool.trim(";", text, "")).toEqual("abc;;;");
            expect(g + ":all right", () -> Tool.trim(":", ";;;;", ";")).toEqual("");
            expect(g + ":all left, right", () -> Tool.trim(";", ";;;;", ";")).toEqual("");
            expect(g + ":spaces", () -> Tool.trim(" 　", "　 　あ　 いうえお 　 ", " 　")).toEqual("あ　 いうえお");
        });

        groupWith("nextMillis", group -> {
            String g = group + ":";
            ZonedDateTime now = ZonedDateTime.now();
            Function<String, Object> from = s -> now.plus(Tool.nextMillis(s.substring(g.length()), now), ChronoUnit.MILLIS);
            Function<Function<ZonedDateTime, ZonedDateTime>, Object> to = d -> d.apply(now);
            expect(g + "all null", () -> Tool.nextMillis(null, null)).toThrow(NullPointerException.class);
            expect(g + "text null", () -> Tool.nextMillis(null, now)).toThrow(NullPointerException.class);
            expect(g + "from null", () -> Tool.nextMillis("", null)).toThrow(NullPointerException.class);
            expectWith(g + "", from).toEqual(now);
            expectWith(g + "Fri 12:34",
                    from).toEqual(
                            to.apply(d -> (d.getDayOfWeek().getValue() < DayOfWeek.FRIDAY.getValue()
                                    || d.getDayOfWeek().getValue() == DayOfWeek.FRIDAY.getValue()
                                            && (d.getHour() < 12 || d.getHour() == 12 && d.getMinute() < 34) ? d
                                                    : d.plusDays(7)).with(ChronoField.DAY_OF_WEEK, DayOfWeek.FRIDAY.getValue()).withHour(12).withMinute(34)
                                                            .truncatedTo(ChronoUnit.MINUTES)));
            expectWith(g + "SUNDAY 11:00", from).toEqual(to.apply(d -> (d.getDayOfWeek().getValue() < DayOfWeek.SUNDAY.getValue()
                    || d.getDayOfWeek().getValue() == DayOfWeek.SUNDAY.getValue() && d.getHour() < 11 ? d : d.plusDays(7))
                            .with(ChronoField.DAY_OF_WEEK, DayOfWeek.SUNDAY.getValue()).withHour(11).truncatedTo(ChronoUnit.HOURS)));
            expectWith(g + "1D", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.DAYS)));
            expectWith(g + "2H", from).toEqual(to.apply(d -> d.plus(2, ChronoUnit.HOURS)));
            expectWith(g + "0.5H", from).toThrow(DateTimeParseException.class);
            expectWith(g + "3M", from).toEqual(to.apply(d -> d.plus(3, ChronoUnit.MINUTES)));
            expectWith(g + "4S", from).toEqual(to.apply(d -> d.plus(4, ChronoUnit.SECONDS)));
            expectWith(g + "100S", from).toEqual(to.apply(d -> d.plus(100, ChronoUnit.SECONDS)));
            expectWith(g + "2", from).toEqual(to.apply(d -> (d.getDayOfMonth() < 2 ? d : d.plusMonths(1)).withDayOfMonth(2).truncatedTo(ChronoUnit.DAYS)));
            expectWith(g + "12:00", from).toEqual(to.apply(d -> (d.getHour() < 12 ? d : d.plusDays(1)).withHour(12).truncatedTo(ChronoUnit.HOURS)));
            expectWith(g + "12:34", from).toEqual(to.apply(d -> (d.getHour() < 12 || d.getHour() == 12 && d.getMinute() < 34 ? d : d.plusDays(1)).withHour(12)
                    .withMinute(34).truncatedTo(ChronoUnit.MINUTES)));
            expectWith(g + ":01", from).toEqual(to.apply(d -> (d.getMinute() < 1 ? d : d.plusHours(1)).withMinute(1).truncatedTo(ChronoUnit.MINUTES)));
            expectWith(g + "::02", from)
                    .toEqual(to.apply(d -> (d.getSecond() < 2 ? d : d.plus(1, ChronoUnit.MINUTES)).withSecond(2).truncatedTo(ChronoUnit.SECONDS)));
            expectWith(g + "12:34:56", from).toEqual(to
                    .apply(d -> (d.getHour() < 12 || d.getHour() == 12 && (d.getMinute() < 34 || d.getMinute() == 34 && d.getSecond() < 56) ? d : d.plusDays(1))
                            .withHour(12).withMinute(34).withSecond(56).truncatedTo(ChronoUnit.SECONDS)));
            expectWith(g + "3 10:00", from).toEqual(to.apply(d -> (d.getDayOfMonth() < 3 || d.getDayOfMonth() == 3 && d.getHour() < 10 ? d : d.plusMonths(1))
                    .withDayOfMonth(3).withHour(10).truncatedTo(ChronoUnit.HOURS)));
            expectWith(g + "2/3 10:00", from).toEqual(
                    to.apply(d -> (d.getMonthValue() < 2 || d.getMonthValue() == 2 && (d.getDayOfMonth() < 3 || d.getDayOfMonth() == 3 && d.getHour() < 10) ? d
                            : d.plusYears(1)).withMonth(2).withDayOfMonth(3).withHour(10).truncatedTo(ChronoUnit.HOURS)));
            expectWith(g + "2020/3/4 12:34:56", from).toEqual(to.apply(d -> ZonedDateTime.of(2020, 3, 4, 12, 34, 56, 0, ZoneId.systemDefault())));
        });
    }
}
