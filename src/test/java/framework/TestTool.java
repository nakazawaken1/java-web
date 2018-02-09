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
public class TestTool extends Tester {
    {
        group("add", g -> {
            Map<String, List<String>> map = new HashMap<>();
            expect(g + ":1", n -> Tool.json(Tool.addValue(map, "key", "value1"))).toEqual(Tool.json("value1"));
            expect(g + ":2", n -> Tool.json(Tool.addValue(map, "key", "value2"))).toEqual(Tool.json("value2"));
        });

        group("trim", g -> {
            String text = ";;;abc;;;";
            expect(g + ":null", n -> Tool.trim(null, null, null)).toNull();
            expect(g + ":both", n -> Tool.trim(";", text, ";")).toEqual("abc");
            expect(g + ":no match", n -> Tool.trim(":", text, ":")).toEqual(text);
            expect(g + ":left null", n -> Tool.trim(null, text, ";")).toEqual(";;;abc");
            expect(g + ":left empty", n -> Tool.trim("", text, ";")).toEqual(";;;abc");
            expect(g + ":all left", n -> Tool.trim(";", ";;;;", ":")).toEqual("");
            expect(g + ":right null", n -> Tool.trim(";", text, null)).toEqual("abc;;;");
            expect(g + ":rihgt empty", n -> Tool.trim(";", text, "")).toEqual("abc;;;");
            expect(g + ":all right", n -> Tool.trim(":", ";;;;", ";")).toEqual("");
            expect(g + ":all left, right", n -> Tool.trim(";", ";;;;", ";")).toEqual("");
            expect(g + ":spaces", n -> Tool.trim(" 　", "　 　あ　 いうえお 　 ", " 　")).toEqual("あ　 いうえお");
        });

        group("nextMillis", group -> {
            String g = group + ":";
            ZonedDateTime now = ZonedDateTime.now();
            Function<String, Object> from = s -> now.plus(Tool.nextMillis(s.substring(g.length()), now), ChronoUnit.MILLIS);
            Function<Function<ZonedDateTime, ZonedDateTime>, Object> to = d -> d.apply(now);
            expect(g + "all null", n -> Tool.nextMillis(null, null)).toThrow(NullPointerException.class);
            expect(g + "text null", n -> Tool.nextMillis(null, now)).toThrow(NullPointerException.class);
            expect(g + "from null", n -> Tool.nextMillis("", null)).toThrow(NullPointerException.class);
            expect(g + "", from).toEqual(now.minusNanos(1000000));
            expect(g + "Fri 12:34",
                    from).toEqual(
                            to.apply(d -> (d.getDayOfWeek().getValue() < DayOfWeek.FRIDAY.getValue()
                                    || d.getDayOfWeek().getValue() == DayOfWeek.FRIDAY.getValue()
                                            && (d.getHour() < 12 || d.getHour() == 12 && d.getMinute() < 34) ? d
                                                    : d.plusDays(7)).with(ChronoField.DAY_OF_WEEK, DayOfWeek.FRIDAY.getValue()).withHour(12).withMinute(34)
                                                            .truncatedTo(ChronoUnit.MINUTES)));
            expect(g + "SUNDAY 11:00", from).toEqual(to.apply(d -> (d.getDayOfWeek().getValue() < DayOfWeek.SUNDAY.getValue()
                    || d.getDayOfWeek().getValue() == DayOfWeek.SUNDAY.getValue() && d.getHour() < 11 ? d : d.plusDays(7))
                            .with(ChronoField.DAY_OF_WEEK, DayOfWeek.SUNDAY.getValue()).withHour(11).truncatedTo(ChronoUnit.HOURS)));
            expect(g + "1D", from).toEqual(to.apply(d -> d.plus(1, ChronoUnit.DAYS)));
            expect(g + "2H", from).toEqual(to.apply(d -> d.plus(2, ChronoUnit.HOURS)));
            expect(g + "0.5H", from).toThrow(DateTimeParseException.class);
            expect(g + "3M", from).toEqual(to.apply(d -> d.plus(3, ChronoUnit.MINUTES)));
            expect(g + "4S", from).toEqual(to.apply(d -> d.plus(4, ChronoUnit.SECONDS)));
            expect(g + "100S", from).toEqual(to.apply(d -> d.plus(100, ChronoUnit.SECONDS)));
            expect(g + "2", from).toEqual(to.apply(d -> (d.getDayOfMonth() < 2 ? d : d.plusMonths(1)).withDayOfMonth(2).truncatedTo(ChronoUnit.DAYS)));
            expect(g + "12:00", from).toEqual(to.apply(d -> (d.getHour() < 12 ? d : d.plusDays(1)).withHour(12).truncatedTo(ChronoUnit.HOURS)));
            expect(g + "12:34", from).toEqual(to.apply(d -> (d.getHour() < 12 || d.getHour() == 12 && d.getMinute() < 34 ? d : d.plusDays(1)).withHour(12)
                    .withMinute(34).truncatedTo(ChronoUnit.MINUTES)));
            expect(g + ":01", from).toEqual(to.apply(d -> (d.getMinute() < 1 ? d : d.plusHours(1)).withMinute(1).truncatedTo(ChronoUnit.MINUTES)));
            expect(g + "::02", from)
                    .toEqual(to.apply(d -> (d.getSecond() < 2 ? d : d.plus(1, ChronoUnit.MINUTES)).withSecond(2).truncatedTo(ChronoUnit.SECONDS)));
            expect(g + "12:34:56", from).toEqual(to
                    .apply(d -> (d.getHour() < 12 || d.getHour() == 12 && (d.getMinute() < 34 || d.getMinute() == 34 && d.getSecond() < 56) ? d : d.plusDays(1))
                            .withHour(12).withMinute(34).withSecond(56).truncatedTo(ChronoUnit.SECONDS)));
            expect(g + "3 10:00", from).toEqual(to.apply(d -> (d.getDayOfMonth() < 3 || d.getDayOfMonth() == 3 && d.getHour() < 10 ? d : d.plusMonths(1))
                    .withDayOfMonth(3).withHour(10).truncatedTo(ChronoUnit.HOURS)));
            expect(g + "2/3 10:00", from).toEqual(
                    to.apply(d -> (d.getMonthValue() < 2 || d.getMonthValue() == 2 && (d.getDayOfMonth() < 3 || d.getDayOfMonth() == 3 && d.getHour() < 10) ? d
                            : d.plusYears(1)).withMonth(2).withDayOfMonth(3).withHour(10).truncatedTo(ChronoUnit.HOURS)));
            expect(g + "2020/3/4 12:34:56", from).toEqual(to.apply(d -> ZonedDateTime.of(2020, 3, 4, 12, 34, 56, 0, ZoneId.systemDefault())));
        });

        group("isDirectory", group -> {
            String g = group + ":";
            Function<String, Object> from = s -> Tool.isDirectory(Tool.toURL(s.substring(g.length())).orElse(null));
            expect(g + "", from).toEqual(true);
            expect(g + "/view", from).toEqual(true);
            expect(g + "/view/index.html", from).toEqual(false);
            expect(g + "view", from).toEqual(true);
            expect(g + "view/", from).toEqual(true);
            expect(g + "view/index.html", from).toEqual(false);
        });

        group("path", g -> {
            expect(g + ":empty elements", n -> Tool.path("", "").apply("/")).toEqual("");
            expect(g + ":a", n -> Tool.path("a").apply("/")).toEqual("a");
            expect(g + ":/a", n -> Tool.path("/a").apply("/")).toEqual("/a");
            expect(g + ":a/", n -> Tool.path("a/").apply("/")).toEqual("a/");
            expect(g + ":/a/", n -> Tool.path("/a/").apply("/")).toEqual("/a/");
            expect(g + ":a,b", n -> Tool.path("a", "b").apply("/")).toEqual("a/b");
            expect(g + ":/a,b", n -> Tool.path("/a", "b").apply("/")).toEqual("/a/b");
            expect(g + ":a/,b", n -> Tool.path("a/", "b").apply("/")).toEqual("a/b");
            expect(g + ":a,/b", n -> Tool.path("a", "/b").apply("/")).toEqual("a/b");
            expect(g + ":a,b/", n -> Tool.path("a", "b/").apply("/")).toEqual("a/b/");
            expect(g + ":a/,/b", n -> Tool.path("a/", "/b").apply("/")).toEqual("a/b");
            expect(g + ":,a,b", n -> Tool.path("", "a", "b").apply("/")).toEqual("a/b");
            expect(g + ":a,,b", n -> Tool.path("a", "", "b").apply("/")).toEqual("a/b");
            expect(g + ":a,b,", n -> Tool.path("a", "b", "").apply("/")).toEqual("a/b");
        });
    }
}
