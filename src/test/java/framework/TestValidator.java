package framework;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import framework.AbstractValidator.ErrorAppender;
import framework.annotation.Range;
import framework.annotation.Time;
import framework.annotation.Valid.All;

/**
 * Validator test
 */
@SuppressWarnings("javadoc")
public class TestValidator extends Tester {

    boolean time(int past, int future, ChronoUnit unit, String input) {
        final Map<String, List<String>> errors = new LinkedHashMap<>();
        ErrorAppender appender = new ErrorAppender() {
            @Override
            public void addError(String name, String value, String error, Object... keyValues) {
                Tool.addValue(errors, name, error);
            }
        };
        String message = Reflector.getDefaultValue(Time.class, "message");
        new Time.Validator(new Time() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return Time.class;
            }

            @Override
            public Class<? extends All>[] value() {
                return Tool.array(All.class);
            }

            @Override
            public int past() {
                return past;
            }

            @Override
            public int future() {
                return future;
            }

            @Override
            public ChronoUnit unit() {
                return unit;
            }

            @Override
            public String message() {
                return message;
            }
        }).validate(All.class, "name", input, appender);
        return !message.equals(Tool.getFirst(errors, "name").orElse(null));
    }

    boolean range(double min, double value, int integerMin, int integerMax, int fractionMin, int fractionMax, String input) {
        final Map<String, List<String>> errors = new LinkedHashMap<>();
        ErrorAppender appender = new ErrorAppender() {
            @Override
            public void addError(String name, String value, String error, Object... keyValues) {
                Tool.addValue(errors, name, error);
            }
        };
        String message = Reflector.getDefaultValue(Range.class, "message");
        new Range.Validator(new Range() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return Range.class;
            }

            @Override
            public String message() {
                return message;
            }

            @Override
            public Class<? extends All>[] groups() {
                return Tool.array(All.class);
            }

            @Override
            public double min() {
                return min;
            }

            @Override
            public double value() {
                return value;
            }

            @Override
            public int integerMin() {
                return integerMin;
            }

            @Override
            public int integerMax() {
                return integerMax;
            }

            @Override
            public int fractionMin() {
                return fractionMin;
            }

            @Override
            public int fractionMax() {
                return fractionMax;
            }
        }).validate(All.class, "name", input, appender);
        return !message.equals(Tool.getFirst(errors, "name").orElse(null));
    }

    {
        group("time", g -> {
            expect(g + ":null", n -> time(Integer.MAX_VALUE, Integer.MAX_VALUE, ChronoUnit.DAYS, null)).toEqual(true);
            expect(g + ":empty", n -> time(Integer.MAX_VALUE, Integer.MAX_VALUE, ChronoUnit.DAYS, "")).toEqual(true);
            expect(g + ":future:ok", n -> time(Integer.MAX_VALUE, 1, ChronoUnit.DAYS, LocalDateTime.now().plusDays(1).minusSeconds(1).toString()))
                    .toEqual(true);
            expect(g + ":future:ng", n -> time(Integer.MAX_VALUE, 1, ChronoUnit.DAYS, LocalDateTime.now().plusDays(1).plusSeconds(1).toString()))
                    .toEqual(false);
            expect(g + ":past:ok", n -> time(1, Integer.MAX_VALUE, ChronoUnit.DAYS, LocalDateTime.now().minusDays(1).plusSeconds(1).toString()))
                    .toEqual(true);
            expect(g + ":past:ng", n -> time(1, Integer.MAX_VALUE, ChronoUnit.DAYS, LocalDateTime.now().minusDays(1).minusSeconds(1).toString()))
                    .toEqual(false);
        });
        group("range", g -> {
            expect(g + ":null", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, null))
                    .toEqual(true);
            expect(g + ":empty", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, ""))
                    .toEqual(true);
            expect(g + ":min:ok", n -> range(-1, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "-1")).toEqual(true);
            expect(g + ":min:ng", n -> range(-1, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "-1.1")).toEqual(false);
            expect(g + ":value:ok", n -> range(Double.NEGATIVE_INFINITY, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "0")).toEqual(true);
            expect(g + ":value:ng", n -> range(Double.NEGATIVE_INFINITY, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "0.1")).toEqual(false);
            expect(g + ":integerMin:ok",
                    n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 2, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "10.12")).toEqual(true);
            expect(g + ":integerMin:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 2, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "9.1"))
                    .toEqual(false);
            expect(g + ":integerMax:ok", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, 0, Integer.MAX_VALUE, "9.1")).toEqual(true);
            expect(g + ":integerMax:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, 0, Integer.MAX_VALUE, "10.12"))
                    .toEqual(false);
            expect(g + ":fractionMin:ok",
                    n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 2, Integer.MAX_VALUE, "10.12")).toEqual(true);
            expect(g + ":fractionMin:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 2, Integer.MAX_VALUE, "9.1"))
                    .toEqual(false);
            expect(g + ":fractionMax:ok", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, 1, "9.1")).toEqual(true);
            expect(g + ":fractionMax:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, 1, "10.12"))
                    .toEqual(false);
        });
    }
}
