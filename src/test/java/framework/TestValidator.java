package framework;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import framework.annotation.Letters;
import framework.annotation.Range;
import framework.annotation.Matches;
import framework.annotation.Required;
import framework.annotation.Time;
import framework.annotation.Valid.All;
import framework.annotation.Validator;
import framework.annotation.Validator.ErrorAppender;
import framework.annotation.Validator.Errors;

/**
 * Validator test
 */
@SuppressWarnings("javadoc")
public class TestValidator extends Tester {

	static class Data {
		@Required
		int id;

		@Required
		String name;
	}

	static class Digit {
		@Letters(Letters.DIGITS)
		int value;
	}

	static class Zenkaku {
		@Letters(value = Letters.ASCII, deny = true)
		String value;
	}

	static class Hankaku {
		@Letters(Letters.ASCII)
		String value;
	}

	static class AlpabetNumber {
		@Letters(Letters.ALPHABETS_NUMBERS)
		String value;
	}

	static class Tel {
		@Letters(Letters.DIGITS + '-')
		String value;
	}

	static class Real {
		@Matches("[+-]?[0-9]+([.][0-9]+)?")
		String value;
	}
	
	static class Items {
	    List<Data> items;
	}

	boolean time(int past, int future, ChronoUnit unit, String input) {
		final Map<String, List<String>> errors = new LinkedHashMap<>();
		ErrorAppender appender = new ErrorAppender() {
			@Override
			public void addError(String name, String value, String error, Object... keyValues) {
				Tool.addValue(errors, name, error, ArrayList::new);
			}
		};
		String message = Reflector.getDefaultValue(Time.class, "message");
		new Time.Validator(new Time() {

			@Override
			public Class<? extends Annotation> annotationType() {
				return Time.class;
			}

			@Override
			public Class<? extends All>[] groups() {
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

	boolean range(double min, double value, int integerMin, int integerMax, int fractionMin, int fractionMax,
			String input) {
		final Map<String, List<String>> errors = new LinkedHashMap<>();
		ErrorAppender appender = (name, x, error, xx) -> Tool.addValue(errors, name, error, ArrayList::new);
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
		String required = Reflector.getDefaultValue(Required.class, "message");
		String letters = Reflector.getDefaultValue(Letters.class, "message");
		String regEx = Reflector.getDefaultValue(Matches.class, "message");
		group("list", g -> {
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, List.class, "list", Tool.map("list[0].id", Arrays.asList("1"),
						"list[0].name", Arrays.asList(""), "list[1].id", Arrays.asList((String) null)), errors, Data.class);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(required, Tool.getFirst(errors, "list[0].name").orElse(null));
				eq.accept(required, Tool.getFirst(errors, "list[1].id").orElse(null));
				eq.accept(required, Tool.getFirst(errors, "list[1].name").orElse(null));
			});
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, List.class, "list", Tool.map("list[0].id", Arrays.asList("1"),
						"list[0].name", Arrays.asList("abc"), "list[1].id", Arrays.asList("2"), "list[1].name", Arrays.asList("def")), errors, Data.class);
				return errors.size();
			}).toEqual(0);
		});
		group("required", g -> {
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Data.class, "data", Tool.map(//
						"data.id", Arrays.asList("1"), //
						"data.name", Arrays.asList("abc")//
				), errors);
				return errors.size();
			}).toEqual(0);
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Data.class, "data", Tool.map(//
						"data.id", Arrays.asList(), //
						"data.name", Arrays.asList()//
				), errors);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(required, Tool.getFirst(errors, "data.id").orElse(null));
				eq.accept(required, Tool.getFirst(errors, "data.name").orElse(null));
			});
		});
		group("digit", g -> {
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Digit.class, "data", Tool.map("data.value", Arrays.asList("12")),
						errors);
				return errors.size();
			}).toEqual(0);
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Digit.class, "data", Tool.map("data.value", Arrays.asList("a")),
						errors);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(letters, Tool.getFirst(errors, "data.value").orElse(null));
			});
		});
		group("zenkaku", g -> {
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Zenkaku.class, "data",
						Tool.map("data.value", Arrays.asList("あいうえお")), errors);
				return errors.size();
			}).toEqual(0);
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Zenkaku.class, "data",
						Tool.map("data.value", Arrays.asList("あいaうえお")), errors);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(letters, Tool.getFirst(errors, "data.value").orElse(null));
			});
		});
		group("hankaku", g -> {
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Hankaku.class, "data",
						Tool.map("data.value", Arrays.asList("abc123!#$")), errors);
				return errors.size();
			}).toEqual(0);
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Hankaku.class, "data",
						Tool.map("data.value", Arrays.asList("abc123!#あ$")), errors);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(letters, Tool.getFirst(errors, "data.value").orElse(null));
			});
		});
		group("alphabet", g -> {
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, AlpabetNumber.class, "data",
						Tool.map("data.value", Arrays.asList("abc123ABC")), errors);
				return errors.size();
			}).toEqual(0);
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, AlpabetNumber.class, "data",
						Tool.map("data.value", Arrays.asList("abc123!ABC")), errors);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(letters, Tool.getFirst(errors, "data.value").orElse(null));
			});
		});
		group("tel", g -> {
			expect(g + ":ok", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Tel.class, "data",
						Tool.map("data.value", Arrays.asList("1234-5678-90")), errors);
				return errors.size();
			}).toEqual(0);
			expect(g + ":ng", n -> {
				Errors errors = new Errors();
				Validator.Manager.validateClass(All.class, Tel.class, "data",
						Tool.map("data.value", Arrays.asList("1234-#5678-90")), errors);
				return errors;
			}).<Errors>toTest((errors, eq) -> {
				eq.accept(letters, Tool.getFirst(errors, "data.value").orElse(null));
			});
		});
        group("real", g -> {
            expect(g + ":ok", n -> {
                Errors errors = new Errors();
                Validator.Manager.validateClass(All.class, Real.class, "data",
                        Tool.map("data.value", Arrays.asList("1.23")), errors);
                return errors.size();
            }).toEqual(0);
            expect(g + ":ng", n -> {
                Errors errors = new Errors();
                Validator.Manager.validateClass(All.class, Real.class, "data",
                        Tool.map("data.value", Arrays.asList("1.2.3")), errors);
                return errors;
            }).<Errors>toTest((errors, eq) -> {
                eq.accept(regEx, Tool.getFirst(errors, "data.value").orElse(null));
            });
        });
        group("items", g -> {
            expect(g + ":ok", n -> {
                Errors errors = new Errors();
                Validator.Manager.validateClass(All.class, Items.class, "data",
                        Tool.map("data.items[0].id", Arrays.asList("1"), "data.items[1].id", Arrays.asList("2"), "data.items[0].name", Arrays.asList("a"), "data.items[1].name", Arrays.asList("b")), errors);
                return errors.size();
            }).toEqual(0);
            expect(g + ":ng", n -> {
                Errors errors = new Errors();
                Map<String, List<String>> parameters = Tool.map("data.items[0].id", Arrays.asList("1"), "data.items[1].id", Arrays.asList(), "data.items[0].name", Arrays.asList(), "data.items[1].name", Arrays.asList("b"));
                Validator.Manager.validateClass(All.class, Data.class, "data.items[0]", parameters, errors);
                Validator.Manager.validateClass(All.class, Data.class, "data.items[1]", parameters, errors);
                return errors;
            }).<Errors>toTest((errors, eq) -> {
                eq.accept(2, errors.size());
                eq.accept(required, Tool.getFirst(errors, "data.items[0].name").orElse(null));
                eq.accept(required, Tool.getFirst(errors, "data.items[1].id").orElse(null));
            });
        });
		group("time", g -> {
			expect(g + ":null", n -> time(Integer.MAX_VALUE, Integer.MAX_VALUE, ChronoUnit.DAYS, null)).toEqual(true);
			expect(g + ":empty", n -> time(Integer.MAX_VALUE, Integer.MAX_VALUE, ChronoUnit.DAYS, "")).toEqual(true);
			expect(g + ":future:ok", n -> time(Integer.MAX_VALUE, 1, ChronoUnit.DAYS,
					LocalDateTime.now().plusDays(1).minusSeconds(1).toString())).toEqual(true);
			expect(g + ":future:ng", n -> time(Integer.MAX_VALUE, 1, ChronoUnit.DAYS,
					LocalDateTime.now().plusDays(1).plusSeconds(1).toString())).toEqual(false);
			expect(g + ":past:ok", n -> time(1, Integer.MAX_VALUE, ChronoUnit.DAYS,
					LocalDateTime.now().minusDays(1).plusSeconds(1).toString())).toEqual(true);
			expect(g + ":past:ng", n -> time(1, Integer.MAX_VALUE, ChronoUnit.DAYS,
					LocalDateTime.now().minusDays(1).minusSeconds(1).toString())).toEqual(false);
		});
		group("range", g -> {
			expect(g + ":null", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0,
					Integer.MAX_VALUE, null)).toEqual(true);
			expect(g + ":empty", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0,
					Integer.MAX_VALUE, "")).toEqual(true);
			expect(g + ":min:ok",
					n -> range(-1, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "-1"))
							.toEqual(true);
			expect(g + ":min:ng",
					n -> range(-1, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "-1.1"))
							.toEqual(false);
			expect(g + ":value:ok",
					n -> range(Double.NEGATIVE_INFINITY, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "0"))
							.toEqual(true);
			expect(g + ":value:ng",
					n -> range(Double.NEGATIVE_INFINITY, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "0.1"))
							.toEqual(false);
			expect(g + ":integerMin:ok", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 2,
					Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "10.12")).toEqual(true);
			expect(g + ":integerMin:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 2,
					Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "9.1")).toEqual(false);
			expect(g + ":integerMax:ok",
					n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, 0, Integer.MAX_VALUE, "9.1"))
							.toEqual(true);
			expect(g + ":integerMax:ng",
					n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, 0, Integer.MAX_VALUE, "10.12"))
							.toEqual(false);
			expect(g + ":fractionMin:ok", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0,
					Integer.MAX_VALUE, 2, Integer.MAX_VALUE, "10.12")).toEqual(true);
			expect(g + ":fractionMin:ng", n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0,
					Integer.MAX_VALUE, 2, Integer.MAX_VALUE, "9.1")).toEqual(false);
			expect(g + ":fractionMax:ok",
					n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, 1, "9.1"))
							.toEqual(true);
			expect(g + ":fractionMax:ng",
					n -> range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, Integer.MAX_VALUE, 0, 1, "10.12"))
							.toEqual(false);
		});
	}
}
