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
        groupWith("time", g -> {
            expectWith(g + ":null", n -> time(Integer.MAX_VALUE, Integer.MAX_VALUE, ChronoUnit.DAYS, null)).toEqual(true);
            expectWith(g + ":empty", n -> time(Integer.MAX_VALUE, Integer.MAX_VALUE, ChronoUnit.DAYS, "")).toEqual(true);
            expectWith(g + ":future:ok", n -> time(Integer.MAX_VALUE, 1, ChronoUnit.DAYS, LocalDateTime.now().plusDays(1).minusSeconds(1).toString()))
                    .toEqual(true);
            expectWith(g + ":future:ng", n -> time(Integer.MAX_VALUE, 1, ChronoUnit.DAYS, LocalDateTime.now().plusDays(1).plusSeconds(1).toString()))
                    .toEqual(false);
            expectWith(g + ":past:ok", n -> time(1, Integer.MAX_VALUE, ChronoUnit.DAYS, LocalDateTime.now().minusDays(1).plusSeconds(1).toString()))
                    .toEqual(true);
            expectWith(g + ":past:ng", n -> time(1, Integer.MAX_VALUE, ChronoUnit.DAYS, LocalDateTime.now().minusDays(1).minusSeconds(1).toString()))
                    .toEqual(false);
        });
        groupWith("range", g -> {
            expectWith(g + ":null", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, null))
                    .toEqual(true);
            expectWith(g + ":empty", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, ""))
                    .toEqual(true);
            expectWith(g + ":min:ok", n -> range(-1, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "-1")).toEqual(true);
            expectWith(g + ":min:ng", n -> range(-1, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "-1.1")).toEqual(false);
            expectWith(g + ":value:ok", n -> range(Double.NEGATIVE_INFINITY, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "0")).toEqual(true);
            expectWith(g + ":value:ng", n -> range(Double.NEGATIVE_INFINITY, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "0.1")).toEqual(false);
            expectWith(g + ":integerMin:ok",
                    n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 2, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "10.12")).toEqual(true);
            expectWith(g + ":integerMin:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 2, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "9.1"))
                    .toEqual(false);
            expectWith(g + ":integerMax:ok", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, 0, Integer.MAX_VALUE, "9.1")).toEqual(true);
            expectWith(g + ":integerMax:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, 0, Integer.MAX_VALUE, "10.12"))
                    .toEqual(false);
            expectWith(g + ":fractionMin:ok",
                    n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 2, Integer.MAX_VALUE, "10.12")).toEqual(true);
            expectWith(g + ":fractionMin:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 2, Integer.MAX_VALUE, "9.1"))
                    .toEqual(false);
            expectWith(g + ":fractionMax:ok", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, 1, "9.1")).toEqual(true);
            expectWith(g + ":fractionMax:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, 1, "10.12"))
                    .toEqual(false);
        });
    }
}
