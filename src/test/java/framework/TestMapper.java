package framework;

import framework.annotation.Mapping;

/**
 * Mapper test
 */
@SuppressWarnings("javadoc")
public class TestMapper extends Tester {

    static class NoMappingTable {
	int loginId;
	String firstName;
    }

    static class Table {
	@Mapping("loginId")
	int id;
	String firstName;
    }

    @Mapping(value = "T_PREFIX_TABLE", mapper = Mapping.ToSnakeUpper.class)
    static class PrefixTable {
	@Mapping("login_id")
	int id;
	String firstName;
    }

    @Mapping(mapper = Mapping.ToSnakeUpper.class)
    static class UpperTable {
	int loginId;
	String firstName;
    }

    @Mapping(mapper = Mapping.ToSnakeLower.class)
    static class LowerTable {
	int loginId;
	String firstName;
    }

    {
	group("Mapper", g -> {
	    group(g + ":NoMappingTable", g2 -> {
		expect(g2, n -> Reflector.mappingClassName(NoMappingTable.class)).toEqual("NoMappingTable");
		expect(g2 + ".loginId",
			n -> Reflector.mappingFieldName(Try.f(NoMappingTable.class::getDeclaredField).apply("loginId")))
				.toEqual("loginId");
		expect(g2 + ".firstName",
			n -> Reflector
				.mappingFieldName(Try.f(NoMappingTable.class::getDeclaredField).apply("firstName")))
					.toEqual("firstName");
	    });
	    group(g + ":Table", g2 -> {
		expect(g2, n -> Reflector.mappingClassName(Table.class)).toEqual("Table");
		expect(g2 + ".loginId",
			n -> Reflector.mappingFieldName(Try.f(Table.class::getDeclaredField).apply("id")))
				.toEqual("loginId");
		expect(g2 + ".firstName",
			n -> Reflector.mappingFieldName(Try.f(Table.class::getDeclaredField).apply("firstName")))
				.toEqual("firstName");
	    });
	    group(g + ":UpperTable", g2 -> {
		expect(g2, n -> Reflector.mappingClassName(UpperTable.class)).toEqual("UPPER_TABLE");
		expect(g2 + ".loginId",
			n -> Reflector.mappingFieldName(Try.f(UpperTable.class::getDeclaredField).apply("loginId")))
				.toEqual("LOGIN_ID");
		expect(g2 + ".firstName",
			n -> Reflector.mappingFieldName(Try.f(UpperTable.class::getDeclaredField).apply("firstName")))
				.toEqual("FIRST_NAME");
	    });
	    group(g + ":LowerTable", g2 -> {
		expect(g2, n -> Reflector.mappingClassName(LowerTable.class)).toEqual("lower_table");
		expect(g2 + ".loginId",
			n -> Reflector.mappingFieldName(Try.f(LowerTable.class::getDeclaredField).apply("loginId")))
				.toEqual("login_id");
		expect(g2 + ".firstName",
			n -> Reflector.mappingFieldName(Try.f(LowerTable.class::getDeclaredField).apply("firstName")))
				.toEqual("first_name");
	    });
	    group(g + ":PrefixTable", g2 -> {
		expect(g2, n -> Reflector.mappingClassName(PrefixTable.class)).toEqual("T_PREFIX_TABLE");
		expect(g2 + ".loginId",
			n -> Reflector.mappingFieldName(Try.f(PrefixTable.class::getDeclaredField).apply("id")))
				.toEqual("login_id");
		expect(g2 + ".firstName",
			n -> Reflector.mappingFieldName(Try.f(PrefixTable.class::getDeclaredField).apply("firstName")))
				.toEqual("FIRST_NAME");
	    });
	});
    }
}
