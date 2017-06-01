package framework;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import app.config.Sys;
import app.model.Account;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Try.QuadFunction;
import framework.Try.TryConsumer;
import framework.Try.TryFunction;
import framework.annotation.Config;
import framework.annotation.Help;
import framework.annotation.Id;
import framework.annotation.Join;
import framework.annotation.Factory;
import framework.annotation.Persist;
import framework.annotation.Size;
import framework.annotation.Stringer;

/**
 * database
 */
public class Db implements AutoCloseable {

    /**
     * support db type
     */
    public static enum Type {

        /**
         * H2
         */
        H2("org.h2.Driver", "org.h2.jdbcx.JdbcDataSource"),
        /**
         * MySQL
         */
        MYSQL("com.mysql.jdbc.Driver", "com.mysql.jdbc.jdbc2.optional.MysqlDataSource"),
        /**
         * PostgreSQL
         */
        POSTGRESQL("org.postgresql.Driver", "org.postgresql.ds.PGSimpleDataSource"),
        /**
         * SQLServer
         */
        SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", "com.microsoft.sqlserver.jdbc.SQLServerDataSource"),
        /**
         * Oracle
         */
        ORACLE("oracle.jdbc.OracleDriver", "oracle.jdbc.pool.OracleDataSource");

        /**
         * default driver
         */
        public final String driver;

        /**
         * default data source
         */
        public final String dataSource;

        /**
         * @param driver driver
         * @param dataSource data source
         */
        private Type(String driver, String dataSource) {
            this.driver = driver;
            this.dataSource = dataSource;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ENGLISH);
        }

        /**
         * @param url URL
         * @return type
         */
        public static Type fromUrl(String url) {
            return Enum.valueOf(Type.class, url.split(":")[1].toUpperCase(Locale.ENGLISH));
        }
    }

    /**
     * example
     *
     * @param args (unuse)
     */
    public static void main(String[] args) {
        class Example {

            public Example(Db db) {
                String table = "test_table";
                Log.info("[tables]");
                db.tables().peek(Log::info).filter(table::equals).forEach(db::drop);
                String[] names = db.create(table, 1, new Column("id").integer(), new Column("name").text(10), new Column("birthday").date(),
                        new Column("weight").decimal(4, 1));
                for (int i = 1; i <= 20; i++) {
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DATE, -i * 31);
                    db.insert(table, names, 1, i, "氏名'" + i, c.getTime(), BigDecimal.valueOf(Math.random() * 80 + 40));
                }
                Log.info(table + " rows: " + db.from(table).count());
                Query q = db.select("name", "birthday", "weight").from(table).where(db.builder.fn("MONTH", "birthday") + " > 6").orderBy("id");
                Log.info("querey rows: " + q.count());
                TryConsumer<ResultSet> printer = row -> {
                    ResultSetMetaData meta = row.getMetaData();
                    IntStream.rangeClosed(1, meta.getColumnCount()).forEach(Try.intC(i -> System.out.println(meta.getColumnName(i) + "=" + row.getObject(i))));
                };
                Log.info("7月以降まれ[1-3]");
                q.limit(3).rows(printer);
                Log.info("7月以降まれ[4-6]");
                q.offset(3).rows(printer);
                Log.info("7月以降まれ[7-]");
                q.offset(6).limit(0).rows(printer);
                db.truncate(table);
                Log.info(table + " rows: " + db.from(table).count());
                db.drop(table);
            }
        }
        try (Db db = Db.connect()) {
            new Example(db);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (Db db = Db.connect(Type.H2, "mem:test")) {
            new Example(db);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (Db db = Db.connect(Type.H2, "~/test")) {
            new Example(db);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * data sources
     */
    private static final Map<String, DataSource> dataSourceMap = new HashMap<>();

    /**
     * resource for cleanup
     */
    private List<ResultSetSpliterator> resources;

    /**
     * connection
     */
    private Connection connection;

    /**
     * SQL builder
     */
    private Builder builder;

    /**
     * database type
     */
    private Type type;

    /**
     * schema
     */
    private String schema;

    /**
     * ResultSet to array
     */
    public static final Function<ResultSet, Object[]> toArray = Try
            .f(rs -> IntStream.rangeClosed(1, rs.getMetaData().getColumnCount()).mapToObj(Try.intF(rs::getObject)).toArray());

    /**
     * ResultSet to map
     */
    public static final Function<ResultSet, Map<String, Object>> toMap = Try.f(rs -> {
        ResultSetMetaData meta = rs.getMetaData();
        return IntStream.rangeClosed(1, meta.getColumnCount()).collect(LinkedHashMap::new,
                Try.intC((map, i) -> map.put(meta.getColumnName(i), rs.getObject(i))), Map::putAll);
    });

    /**
     * connect by config
     *
     * @return db
     */
    public static Db connect() {
        return connect("");
    }

    /**
     * connect by config suffix
     *
     * @param suffix suffix
     * @return db
     */
    public static Db connect(String suffix) {
        try {
            Connection connection = getDataSource(suffix).getConnection();
            Type type = Type.fromUrl(Config.Injector.getSource(Sys.class, Session.currentLocale()).getProperty("Sys.db" + suffix));
            return new Db(connection, type);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * connect direct
     *
     * @param type database type
     * @param name schema
     * @return db
     */
    public static Db connect(Type type, String name) {
        return connect(type, name, null, null, null, 0);
    }

    /**
     * connect direct
     *
     * @param type database type
     * @param name schema
     * @param user user name
     * @param password password
     * @return db
     */
    public static Db connect(Type type, String name, String user, String password) {
        return connect(type, name, user, password, null, 0);
    }

    /**
     * connect direct
     *
     * @param type database type
     * @param name schema
     * @param user user name
     * @param password password
     * @param host host
     * @return db
     */
    public static Db connect(Type type, String name, String user, String password, String host) {
        return connect(type, name, user, password, host, 0);
    }

    /**
     * connect direct
     *
     * @param type database type
     * @param name schema
     * @param user user name
     * @param password password
     * @param host host
     * @param port port
     * @return db
     */
    public static Db connect(Type type, String name, String user, String password, String host, int port) {
        String pad = "//";
        String pad2 = "/";
        String url = null;
        switch (type) {
        case H2:
            url = name;
            break;
        case POSTGRESQL:
            if (port <= 0) {
                port = 5432;
            }
            break;
        case MYSQL:
            if (port <= 0) {
                port = 3306;
            }
            break;
        case ORACLE:
            pad = "thin:@";
            if (port <= 0) {
                port = 1521;
            }
            break;
        case SQLSERVER:
            pad2 = ";database=";
            if (port <= 0) {
                port = 1433;
            }
            break;
        }
        if (host == null) {
            host = "localhost";
        }
        if (url == null) {
            url = pad + host + ":" + port + pad2 + name;
        }
        url = "jdbc:" + type + ":" + url;
        try {
            return new Db(DriverManager.getConnection(url, user, password), type);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * constructor
     *
     * @param connection connection
     * @param type database type
     */
    public Db(Connection connection, Type type) {
        this.connection = connection;
        this.type = type;
        try {
            connection.setAutoCommit(false);
            Log.config("Connection created #" + connection.hashCode() + ", type = " + type + ", autoCommit = " + connection.getAutoCommit());
        } catch (SQLException e) {
            Log.warning(e, () -> "setAutoCommit failed");
        }
        switch (type) {
        case POSTGRESQL:
            builder = new PostgresqlBuilder();
            schema = "public";
            break;
        case ORACLE:
            builder = new OracleBuilder();
            schema = Try.s(connection::getSchema).get();
            break;
        case SQLSERVER:
            builder = new SqlserverBuilder();
            schema = "dbo";
            break;
        case H2:
        case MYSQL:
            builder = new Builder();
            break;
        }
    }

    /**
     * get data source
     *
     * @param suffix Config.db_suffix
     * @return data source
     */
    public static synchronized DataSource getDataSource(String suffix) {
        return dataSourceMap.computeIfAbsent(suffix, Try.f(key -> {
            Properties p = Config.Injector.getSource(Sys.class, Session.currentLocale());
            String url = p.getProperty("Sys.db" + key);
            Type type = Type.fromUrl(url);
            String name = Tool.string(p.getProperty("Sys.Db.datasource_class" + key)).orElse(type.dataSource);
            Class<DataSource> c = Reflector.<DataSource>clazz(name).orElseThrow(() -> new RuntimeException("class not found : " + name));
            DataSource ds = Reflector.instance(c);
            if (type == Type.H2) { /* hack: h2 Duplicate property "USER" */
                List<String> list = new ArrayList<>();
                for (String s : url.split("\\s*;\\s*")) {
                    String[] a = s.split("\\s*=\\s*", 2);
                    if ("user".equalsIgnoreCase(a[0])) {
                        if (a.length > 1) {
                            c.getMethod("setUser", String.class).invoke(ds, a[1]);
                        }
                    } else {
                        list.add(s);
                    }
                }
                url = String.join("; ", list);
            }
            for (String method : Tool.array("setURL", "setUrl")) {
                try {
                    c.getMethod(method, String.class).invoke(ds, url);
                    break;
                } catch (NoSuchMethodException e) {
                    continue;
                }
            }
            Log.info("DataSource created #" + ds);
            return ds;
        }));
    }

    /**
     * @return connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * @return self
     */
    public Db withTransaction() {
        Try.r(() -> connection.setAutoCommit(false)).run();
        return this;
    }

    /**
     * rollback flag
     */
    boolean isRollback = false;

    /**
     * @return self
     */
    public Db rollback() {
        isRollback = true;
        return this;
    }

    /**
     * @return database type
     */
    public Type getType() {
        return type;
    }

    /**
     * @return builder
     */
    public Builder getBuilder() {
        return builder;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() {
        try {
            if (resources != null) {
                resources.forEach(ResultSetSpliterator::close);
            }
            if (isRollback) {
                connection.rollback();
            } else {
                connection.commit();
            }
            connection.close();
            Log.config("Connection dropped #" + connection.hashCode() + " " + (isRollback ? "rollback" : "commit"));
            connection = null;
        } catch (SQLException e) {
            Log.warning(e, () -> "Connection close error");
        }
    }

    /**
     * raw sql
     *
     * @param sql SQL
     * @param map key value map(${key} to value)
     * @param values values(replace {0}, {1}... to value)
     * @return raw sql
     */
    public String sql(String sql, Map<String, Object> map, Object... values) {
        return Formatter.format(sql, Formatter::excludeForSql, builder::escape, Session.currentLocale(), map, values);
    }

    /**
     * preparedQuery(single sql only)
     * 
     * {@code [example] db.preparedQuery("SELECT name FROM account ORDER BY 1", null).map(rs -> rs.getString(1)).forEach(System.out::println);}
     *
     * @param sql SQL
     * @param map name value map({key} : value)
     * @param values values({0}, {1}...)
     * @return ResultSet stream
     */
    @SuppressFBWarnings("OBL_UNSATISFIED_OBLIGATION")
    public Stream<ResultSet> query(String sql, Map<String, Object> map, Object... values) {
        sql = sql(sql, map, values);
        Log.info(sql + ";");
        try {
            return stream(connection.prepareStatement(sql));
        } catch (SQLException e) {
            Try.r(connection::rollback).run();
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * preparedQuery form file(single sql only)
     *
     * @param name SQL file(with extension)
     * @param map name value map({key} : value)
     * @param values values({0}, {1}...)
     * @return ResultSet stream
     */
    public Stream<ResultSet> queryFile(String name, Map<String, Object> map, Object... values) {
        return getSQL(name).map(sql -> query(sql, map, values)).orElseThrow(() -> new UncheckedIOException(new FileNotFoundException(name)));
    }

    /**
     * execute(multi sql support)
     *
     * @param sql SQL
     * @param map name value map({key} : value)
     * @param values values({0}, {1}...)
     * @return affected rows
     */
    public int execute(String sql, Map<String, Object> map, Object... values) {
        int total = 0;
        for (String s : sql.split(";[ \t\r]*\n\\s*")) {
            s = sql(Tool.trim("", s, ";"), map, values);
            Integer hash = null;
            try (PreparedStatement ps = connection.prepareStatement(s)) {
                hash = ps.hashCode();
                Log.config("PreparedStatement created #" + hash);
                Log.info(s + ";");
                total += ps.executeUpdate();
            } catch (SQLException e) {
                Try.r(connection::rollback).run();
                throw new UncheckedSQLException(e);
            } finally {
                Log.config("PreparedStatement dropped #" + hash);
            }
        }
        return total;
    }

    /**
     * execute from file(multi sql support)
     *
     * @param name SQL file(with extension)
     * @param map name value map({key} : value)
     * @param values values({0}, {1}...)
     * @return affected rows
     */
    public int executeFile(String name, Map<String, Object> map, Object... values) {
        return getSQL(name).map(sql -> execute(sql, map, values)).orElseThrow(() -> new UncheckedIOException(new FileNotFoundException(name)));
    }

    /**
     * load sql from resource(database type considered)
     *
     * @param name SQL file(with extension)
     * @return SQL
     */
    public Optional<String> getSQL(String name) {
        String folder = Sys.sql_folder;
        String path = folder + type + '/' + name;
        String commonPath = folder + name;
        Optional<URL> url = Tool.toURL(path);
        if (!url.isPresent()) {
            url = Tool.toURL(commonPath);
        }
        Log.info(url.map(URL::toString).orElse(name + " not found"));
        return url.map(Try.f(i -> builder.replace(Tool.loadText(i.openStream()))));
    }

    /**
     * update if exists row, else insert
     *
     * @param table table name
     * @param names row names(arrange primary key in left)
     * @param primary primary key columns
     * @param values save values
     * @return true: inserted、 false: updated
     */
    public boolean save(String table, String[] names, int primary, Object... values) {
        Query q = from(table);
        boolean empty = false;
        for (int i = 0; i < primary; i++) {
            if (values[i] == null) {
                empty = true;
                break;
            }
            q.where(names[i], values[i]);
        }
        if (!empty) {
            empty = !q.forUpdate().exists();
        }
        if (empty) {
            insert(table, names, primary, values);
        } else if (names.length > primary) {
            update(table, names, primary, values);
        }
        return empty;
    }

    /**
     * update row
     *
     * @param table table name
     * @param names row names(arrange primary key in left)
     * @param primary primary key columns
     * @param values save values
     * @return updated rows
     */
    public int update(String table, String[] names, int primary, Object... values) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(table);
        String pad = " SET ";
        for (int i = primary; i < values.length; i++) {
            sql.append(pad).append(names[i]).append(" = ").append(builder.escape(values[i]));
            pad = ", ";
        }
        pad = " WHERE ";
        for (int i = 0; i < primary; i++) {
            sql.append(pad).append(names[i]).append(" = ").append(builder.escape(values[i]));
            pad = " AND ";
        }
        return execute(sql.toString(), null);
    }

    /**
     * insert row
     *
     * @param table table name
     * @param names row names(arrange primary key in left)
     * @param primary primary key columns
     * @param values save values
     * @return inserted rows
     */
    public int insert(String table, String[] names, int primary, Object... values) {
        Map<String, Object> unique = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            unique.put(names[i], values[i]);
        }
        List<String> nameList = new ArrayList<>(unique.size());
        List<Object> valueList = new ArrayList<>(unique.size());
        for (Map.Entry<String, Object> i : unique.entrySet()) {
            nameList.add(i.getKey());
            valueList.add(i.getValue());
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table).append(join("(", nameList, ", "));
        boolean first = true;
        for (Object value : valueList) {
            sql.append(first ? ") VALUES(" : ", ");
            if (first && primary == 1 && value == null) {
                /* auto id if primary key is single and null */
                sql.append("(SELECT COALESCE(MAX(" + names[0] + "), 0) + 1 FROM " + table + ")");
            } else {
                sql.append(builder.escape(value));
            }
            first = false;
        }
        return execute(sql.append(")").toString(), null);
    }

    /**
     * delete row
     *
     * @param table table name
     * @param names row names(arrange primary key in left)
     * @param primary primary key columns
     * @param values save values
     * @return deleted rows
     */
    public int delete(String table, String[] names, int primary, Object... values) {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(table);
        String pad = " WHERE ";
        for (int i = 0; i < primary; i++) {
            sql.append(pad).append(names[i]).append(" = ").append(builder.escape(values[i]));
            pad = " AND ";
        }
        return execute(sql.toString(), null);
    }

    /**
     * delete all row
     *
     * @param table table name
     */
    public void truncate(String table) {
        execute("TRUNCATE TABLE " + table, null);
    }

    /**
     * @param rs ResultSet
     * @return ResultSet stream
     */
    public Stream<ResultSet> stream(ResultSet rs) {
        ResultSetSpliterator i = new ResultSetSpliterator(rs);
        if (resources == null) {
            resources = new ArrayList<>();
        }
        resources.add(i);
        return StreamSupport.stream(i, false).onClose(i::close);
    }

    /**
     * @param ps PreparedStatement
     * @return ResultSet stream
     */
    public Stream<ResultSet> stream(PreparedStatement ps) {
        ResultSetSpliterator i = new ResultSetSpliterator(ps);
        if (resources == null) {
            resources = new ArrayList<>();
        }
        resources.add(i);
        return StreamSupport.stream(i, false).onClose(i::close);
    }

    /**
     * @param sql SQL
     * @return ResultSet stream
     */
    public Stream<ResultSet> stream(String sql) {
        try {
            Log.info(sql + ";");
            ResultSetSpliterator i = new ResultSetSpliterator(connection.prepareStatement(sql));
            if (resources == null) {
                resources = new ArrayList<>();
            }
            resources.add(i);
            return StreamSupport.stream(i, false).onClose(i::close);
        } catch (SQLException e) {
            Try.r(connection::rollback).run();
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * @return table names(lower case)
     */
    public Stream<String> tables() {
        try {
            return stream(connection.getMetaData().getTables(null, schema, null, new String[] { "TABLE" })).map(Try.f(rs -> rs.getString(3).toLowerCase()));
        } catch (SQLException e) {
            Try.r(connection::rollback).run();
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * SQL function(database type considered)
     *
     * @param function NOW, YEAR, MONTH, DAY...
     * @param args arguments
     * @return native sql
     */
    public String fn(String function, String... args) {
        return builder.fn(function, args);
    }

    /**
     * concatenate array
     *
     * @param <T> value type
     * @param prefix prefix(allow empty)
     * @param items values
     * @param pad separator
     * @return joined string
     */
    public static <T> String join(String prefix, Iterable<T> items, String pad) {
        if (pad == null) {
            pad = "";
        }
        StringBuilder result = new StringBuilder();
        if (items != null) {
            for (Object item : items) {
                result.append(pad).append(item);
            }
        }
        return result.length() <= 0 ? "" : prefix + result.substring(pad.length());
    }

    /**
     * SELECT clause(add field with multiple call)
     *
     * @param fields fields
     * @return Query
     */
    public Query select(String... fields) {
        Query q = new Query(this);
        q.fields = Tool.list(fields);
        return q;
    }

    /**
     * SELECT clause(add field with multiple call)
     *
     * @param fields fields
     * @return Query
     */
    public Query select(Enum<?>... fields) {
        Query q = new Query(this);
        q.fields = Stream.of(fields).map(field -> {
            Class<?> clazz = Reflector.getGenericParameter(field.getClass().getDeclaringClass(), 0);
            return Reflector.mappingFieldName(Reflector.field(clazz, field.name()).get());
        }).collect(Collectors.toList());
        return q;
    }

    /**
     * FROM clause(can use without select)
     *
     * @param table table
     * @return Query
     */
    public Query from(String table) {
        Query q = new Query(this);
        q.table = table;
        return q;
    }

    /**
     * FROM clause(can use without select)
     *
     * @param table table
     * @return Query
     */
    public Query from(Class<?> table) {
        Query q = new Query(this);
        q.table = Reflector.mappingClassName(table);
        return q;
    }

    /**
     * create table
     *
     * @param table table name
     * @param primary primary key columns
     * @param columns columns
     * @return column names
     */
    public String[] create(String table, int primary, Column... columns) {
        execute(createSql(table, primary, columns), null);
        return Stream.of(columns).map(column -> column.name).toArray(String[]::new);
    }

    /**
     * create table
     *
     * @param table table name
     * @param primary primary key columns
     * @param columns columns
     * @return column names
     */
    public String createSql(String table, int primary, Column... columns) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(table).append("(");
        String pad = "";
        String[] names = new String[columns.length];
        int i = 0;
        for (Column column : columns) {
            names[i++] = column.name;
            sql.append(pad).append(column.name).append(" ").append(builder.type(column));
            if (!column.nullable && builder.supportNullString) {
                sql.append(" NOT NULL");
            }
            if (!(column.value == null || !Tool.string(column.value).isPresent() && !builder.supportNullString)) {
                sql.append(" DEFAULT ").append(builder.escape(column.value));
            }
            Tool.string(column.display).map(j -> " COMMENT '" + j + "'").ifPresent(sql::append);
            pad = ", ";
        }
        if (primary > 0) {
            sql.append(join(", PRIMARY KEY(", Tool.list(names).subList(0, primary), ", ")).append(")");
        }
        return sql.append(")").toString();
    }

    /**
     * drop table
     *
     * @param table table name
     */
    public void drop(String table) {
        execute("DROP TABLE " + table, null);
    }

    /**
     * setup mode
     */
    public static enum Setup {
        /**
         * drop and create and insert
         */
        CREATE,
        /**
         * create and insert if not exists
         */
        UPDATE,
        /**
         * delete and insert if exists, create and insert if not exists
         */
        RELOAD,
        /**
         * no operation
         */
        NONE,;
    }

    /**
     * setup database
     *
     * @param setup setup mode
     */
    public static void setup(Setup setup) {
        boolean create = false;
        boolean reload = false;
        switch (setup) {
        case CREATE:
            create = true;
            break;
        case UPDATE:
            break;
        case RELOAD:
            reload = true;
            break;
        case NONE:
            return;
        }

        /* get all sql files(object.*.sql or data.*.sql) */
        String folder = Sys.sql_folder;
        String tablePrefix = "object.";
        String dataPrefix = "data.";
        String suffix = ".sql";
        List<String> all;
        try (Stream<String> files = Tool.getResources(folder)) {
            all = files.filter(file -> file.endsWith(suffix) && (file.startsWith(tablePrefix) || file.startsWith(dataPrefix))).peek(Log::info)
                    .collect(Collectors.toList());
        }
        List<String> datas = all.stream().filter(file -> file.startsWith(dataPrefix)).collect(Collectors.toList());

        try (Db db = Db.connect()) {
            db.getSQL("setup" + suffix).map(sql -> db.execute(sql, null));

            /* get table creation sql */
            List<String> tables = all.stream().filter(file -> file.startsWith(tablePrefix)).collect(Collectors.toList());

            /* table name getter */
            Function<String, String> tableName = file -> file.substring(tablePrefix.length(), file.length() - suffix.length());
            List<String> tableNames = tables.stream().map(tableName).collect(Collectors.toList());

            List<Tuple<String, Class<?>>> models = new ArrayList<>();
            List<Tuple<String, Persist>> modelDatas = new ArrayList<>();
            try (Stream<Class<?>> classes = Tool.getClasses(Account.class.getPackage().getName())) {
                classes.map(c -> Tuple.of(c, c.getAnnotation(Persist.class))).filter(t -> t.r != null)
                        .map(t -> Tuple.of(Reflector.mappingClassName(t.l), t.l, t.r)).filter(t -> !tableNames.contains(t.l)).peek(t -> {
                            if (!datas.contains(t.l)) {
                                modelDatas.add(Tuple.of(t.l, t.r.r));
                            }
                        }).filter(t -> !tables.contains(tablePrefix + t.l + suffix)).forEach(t -> models.add(Tuple.of(t.l, t.r.l)));
            }

            /* get exists tables */
            Set<String> existTables = db.tables().collect(Collectors.toSet());

            Set<String> reloadTables;
            if (create) {
                /* drop exists tables */
                Stream.concat(tableNames.stream(), models.stream().map(t -> t.l)).filter(existTables::contains).forEach(db::drop);
                /* create all tables and entry to load data */
                reloadTables = Stream.concat(tables.stream().peek(file -> db.executeFile(file, null)).map(tableName),
                        models.stream().peek(t -> db.create(t.r)).map(t -> t.l)).collect(Collectors.toSet());
            } else if (reload) {
                /* create no-exists tables */
                tables.stream().filter(file -> !existTables.contains(tableName.apply(file))).forEach(file -> db.executeFile(file, null));
                models.stream().filter(t -> !existTables.contains(t.l)).forEach(t -> db.create(t.r));
                /* entry all tables to load data */
                reloadTables = tableNames.stream().collect(Collectors.toSet());
            } else {
                /* create no-exists table and entry to load data */
                reloadTables = Stream
                        .concat(tables.stream().filter(file -> !existTables.contains(tableName.apply(file))).peek(file -> db.executeFile(file, null))
                                .map(tableName), models.stream().filter(t -> !existTables.contains(t.l)).peek(t -> db.create(t.r)).map(t -> t.l))
                        .collect(Collectors.toSet());
            }

            /* data load */
            Function<String, String> dataName = file -> file.substring(dataPrefix.length(), file.length() - suffix.length());
            datas.stream().filter(file -> reloadTables.contains(dataName.apply(file))).peek(file -> db.truncate(dataName.apply(file)))
                    .forEach(file -> db.executeFile(file, null));
            modelDatas.stream().peek(t -> db.truncate(t.l)).forEach(t -> db.insert(t.l, t.r.field(), t.r.value()));
        }
    }

    /**
     * cleanup
     *
     * @throws Exception exception
     */
    public static void shutdown() throws Exception {
        Class.forName("com.mysql.jdbc.AbandonedConnectionCleanupThread").getMethod("shutdown").invoke(null);
    }

    /**
     * sql builder for MySQL and H2
     */
    public static class Builder {

        /**
         * differentiate NULL from empty
         */
        public boolean supportNullString = true;

        /**
         * build SQL
         *
         * @param q Query
         * @return SQL
         */
        public String sql(Query q) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(q.fields == null ? "*" : join("", q.fields, ", "));
            if (q.table != null) {
                sql.append(" FROM ").append(q.table);
                sql.append(join(" WHERE ", q.wheres, " AND "));
                sql.append(join(" GROUP BY ", q.groups, ", "));
                sql.append(join(" HAVING ", q.havings, " AND "));
                sql.append(join(" ORDER BY ", q.orders, ", "));
                if (q.limit > 0 || q.offset > 0) {
                    long limit = q.limit <= 0 ? Integer.MAX_VALUE : q.limit;
                    sql.append(" LIMIT ").append(q.offset).append(", ").append(limit);
                }
                if (q.forUpdate) {
                    sql.append(" FOR UPDATE");
                }
            }
            return sql.toString();
        }

        /**
         * build SQL
         *
         * @param q Query
         * @return SQL
         */
        public String deleteSql(Query q) {
            StringBuilder sql = new StringBuilder();
            sql.append("DELETE");
            sql.append(" FROM ").append(q.table);
            sql.append(join(" WHERE ", q.wheres, " AND "));
            return sql.toString();
        }

        /**
         * build count sql
         *
         * @param sql select SQL
         * @return count SQL
         */
        public String countSql(String sql) {
            return "SELECT COUNT(*) FROM (" + sql + ") T__";
        }

        /**
         * get function sql
         *
         * @param function function name
         * @param args arguments
         * @return native sql
         */
        public String fn(String function, String... args) {
            return function + "(" + join("", Tool.list(args), ", ") + ")";
        }

        /**
         * escape value
         *
         * @param value value
         * @return escaped value
         */
        @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
        public String escape(Object value) {
            if (value == null) {
                return "NULL";
            }
            if (value instanceof Date) {
                return "TIMESTAMP '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value) + "'";
            }
            if (value instanceof LocalDateTime || value instanceof ZonedDateTime || value instanceof OffsetDateTime) {
                return "TIMESTAMP '" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format((TemporalAccessor) value) + "'";
            }
            if (value instanceof LocalDate) {
                return "DATE '" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format((TemporalAccessor) value) + "'";
            }
            if (value instanceof Number) {
                return String.valueOf(value);
            }
            return "'" + value.toString().replace("'", "''") + "'";
        }

        /**
         * column type for SQL
         *
         * @param column column
         * @return column type for SQL
         */
        public Object type(Column column) {
            Class<?> type = column.type;
            if (Date.class.isAssignableFrom(type) || Temporal.class.isAssignableFrom(type)) {
                return "DATE";
            }
            if (BigDecimal.class.isAssignableFrom(type) || type == float.class || type == double.class) {
                return "DECIMAL(" + column.length + ", " + column.scale + ")";
            }
            if (Number.class.isAssignableFrom(type) || type == byte.class || type == short.class || type == int.class || column.type == long.class) {
                return "INTEGER";
            }
            if (Enum.class.isAssignableFrom(type) && IntSupplier.class.isAssignableFrom(type)) {
                return "INTEGER";
            }
            return "VARCHAR(" + (column.length > 0 ? column.length : 255) + ")";
        }

        /**
         * replace common SQL to native sql
         *
         * @param sql common SQL
         * @return native SQL
         */
        public String replace(String sql) {
            return Pattern.compile("VARCHAR2", Pattern.CASE_INSENSITIVE).matcher(sql).replaceAll("VARCHAR");
        }
    }

    /**
     * sql builder for PostgreSQL
     */
    public static class PostgresqlBuilder extends Builder {

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#sql(framework.Db.Query)
         */
        @Override
        public String sql(Query q) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(q.fields == null ? "*" : join("", q.fields, ", "));
            if (q.table != null) {
                sql.append(" FROM ").append(q.table);
                sql.append(join(" WHERE ", q.wheres, " AND "));
                sql.append(join(" GROUP BY ", q.groups, ", "));
                sql.append(join(" ORDER BY ", q.orders, ", "));
                if (q.limit > 0) {
                    sql.append(" LIMIT ").append(q.limit);
                }
                if (q.offset > 0) {
                    sql.append(" OFFSET ").append(q.offset);
                }
                if (q.forUpdate) {
                    sql.append(" FOR UPDATE");
                }
            }
            return sql.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#fn(java.lang.String, java.lang.String[])
         */
        @Override
        public String fn(String function, String... args) {
            switch (function.toUpperCase(Locale.ENGLISH)) {
            case "YEAR":
            case "MONTH":
            case "DAY":
                return "DATE_PART('" + function + "'" + join(", ", Tool.list(args), ", ") + ")";
            }
            return function + "(" + join("", Tool.list(args), ", ") + ")";
        }
    }

    /**
     * sql builder for SQLServer
     */
    public static class SqlserverBuilder extends Builder {

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#sql(framework.Db.Query)
         */
        @Override
        public String sql(Query q) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            if (q.limit > 0 && q.offset <= 0) {
                sql.append("TOP ").append(q.limit).append(" ");
            }
            sql.append(q.fields == null ? "*" : join("", q.fields, ", "));
            String orderBy = join(" ORDER BY ", q.orders, ", ");
            boolean range = q.offset > 0;
            if (range) {
                String select = sql.toString();
                if (orderBy.isEmpty()) {
                    orderBy = " ORDER BY " + (q.fields == null ? "id" : q.fields.get(0));
                }
                sql.append(" FROM (").append(select).append(", ROW_NUMBER()");
                if (!orderBy.isEmpty()) {
                    sql.append(" OVER(" + orderBy.substring(1) + ")");
                }
                sql.append(" N__");
            }
            if (q.table != null) {
                sql.append(" FROM ").append(q.table);
                if (q.forUpdate) {
                    sql.append(" WITH (UPDLOCK)");
                }
                sql.append(join(" WHERE ", q.wheres, " AND "));
                sql.append(join(" GROUP BY ", q.groups, ", "));
                if (range) {
                    sql.append(") T__ WHERE N__");
                    if (q.limit <= 0) {
                        sql.append(" > ").append(q.offset);
                    } else {
                        sql.append(" BETWEEN ").append(q.offset + 1).append(" AND ").append(q.offset + q.limit);
                    }
                    sql.append(" ORDER BY N__");
                } else {
                    sql.append(orderBy);
                }
            }
            return sql.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#escape(java.lang.Object)
         */
        @Override
        @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
        public String escape(Object value) {
            if (value != null && value instanceof Date) {
                return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value) + "'";
            }
            if (value instanceof LocalDateTime || value instanceof ZonedDateTime || value instanceof OffsetDateTime) {
                return "'" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format((TemporalAccessor) value) + "'";
            }
            if (value instanceof LocalDate) {
                return "'" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format((TemporalAccessor) value) + "'";
            }
            return super.escape(value);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#countSql(java.lang.String)
         */
        @Override
        public String countSql(String sql) {
            int orderBy = sql.lastIndexOf("ORDER BY");
            if (orderBy > 0 && sql.indexOf(" TOP ") < 0) {
                sql = sql.substring(0, orderBy);
            }
            return "SELECT COUNT(*) FROM (" + sql + ") T__";
        }
    }

    /**
     * sql builder for Oracle
     */
    public static class OracleBuilder extends Builder {

        /**
         * constructor
         */
        public OracleBuilder() {
            supportNullString = false;
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#fn(java.lang.String, java.lang.String[])
         */
        @Override
        public String fn(String function, String... args) {
            switch (function.toUpperCase(Locale.ENGLISH)) {
            case "NOW":
                return "SYSDATE";
            case "YEAR":
            case "MONTH":
            case "DAY":
                return "EXTRACT(" + function + " FROM " + join("", Tool.list(args), ", ") + ")";
            }
            return function + "(" + join("", Tool.list(args), ", ") + ")";
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#sql(framework.Db.Query)
         */
        @Override
        public String sql(Query q) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            sql.append(q.fields == null ? "*" : join("", q.fields, ", "));
            String orderBy = join(" ORDER BY ", q.orders, ", ");
            if (q.table != null) {
                sql.append(" FROM ").append(q.table).append(join(" WHERE ", q.wheres, " AND "));
                sql.append(join(" GROUP BY ", q.groups, ", "));
                sql.append(join(" HAVING ", q.havings, " AND "));
                sql.append(orderBy);
                if (q.limit > 0 || q.offset > 0) {
                    sql.insert(0, "SELECT * FROM (SELECT T__.*, ROWNUM N__ FROM (").append(") T__) WHERE N__");
                    if (q.offset <= 0) {
                        sql.append(" <= ").append(q.limit);
                    } else if (q.limit <= 0) {
                        sql.append(" > ").append(q.offset);
                    } else {
                        sql.append(" BETWEEN ").append(q.offset + 1).append(" AND ").append(q.offset + q.limit);
                    }
                }
                if (q.forUpdate) {
                    sql.append(" FOR UPDATE");
                }
            } else {
                sql.append(" FROM DUAL");
            }
            return sql.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Db.Builder#replace(java.lang.String)
         */
        @Override
        public String replace(String sql) {
            return sql;
        }
    }

    /**
     * Column definition
     */
    public static class Column {

        /**
         * name
         */
        String name;
        /**
         * display name
         */
        String display;
        /**
         * type
         */
        Class<?> type;
        /**
         * length(precision part length if numeric type)
         */
        int length;
        /**
         * scale part length
         */
        int scale;
        /**
         * allow null
         */
        boolean nullable;
        /**
         * default value
         */
        Object value;

        /**
         * constructor
         *
         * @param name name
         */
        public Column(String name) {
            this.name = name;
        }

        /**
         * set date type
         *
         * @return column
         */
        public Column date() {
            type = Date.class;
            return this;
        }

        /**
         * set integer type
         *
         * @return column
         */
        public Column integer() {
            type = Long.class;
            return this;
        }

        /**
         * set decimal type
         *
         * @param precision precision
         * @param scale scale
         * @return column
         */
        public Column decimal(int precision, int scale) {
            type = BigDecimal.class;
            length = precision;
            this.scale = scale;
            return this;
        }

        /**
         * set text type
         *
         * @return column
         */
        public Column text() {
            type = String.class;
            return this;
        }

        /**
         * set text type and length
         *
         * @param length length
         * @return column
         */
        public Column text(int length) {
            type = String.class;
            this.length = length;
            return this;
        }

        /**
         * set display name
         *
         * @param display display name
         * @return column
         */
        public Column display(String display) {
            this.display = display;
            return this;
        }

        /**
         * set default value
         *
         * @param value default value
         * @return column
         */
        public Column value(String value) {
            this.value = value;
            return this;
        }

        /**
         * set length
         *
         * @param length length
         * @return column
         */
        public Column length(int length) {
            this.length = length;
            return this;
        }

        /**
         * set nullable
         *
         * @return column
         */
        public Column nullable() {
            this.nullable = true;
            return this;
        }
    }

    /**
     * spliterator of ResultSet
     */
    public static class ResultSetSpliterator implements AutoCloseable, Spliterator<ResultSet> {

        /**
         * prepared statement
         */
        PreparedStatement ps;

        /**
         * result set
         */
        ResultSet rs;

        /**
         * constructor
         *
         * @param rs result set
         */
        public ResultSetSpliterator(ResultSet rs) {
            this.rs = rs;
            Log.config("ResultSet created #" + rs.hashCode());
        }

        /**
         * constructor
         *
         * @param ps prepared statement
         */
        public ResultSetSpliterator(PreparedStatement ps) {
            this.ps = ps;
            Log.config("PreparedStatement created #" + ps.hashCode());
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.AutoCloseable#close()
         */
        @Override
        public void close() {
            try {
                if (rs != null) {
                    rs.close();
                    Log.config("ResultSet dropped #" + rs.hashCode());
                    rs = null;
                }
            } catch (SQLException e) {
                Log.warning(e, () -> "ResultSet close error");
            }
            try {
                if (ps != null) {
                    ps.close();
                    Log.config("PreparedStatement dropped #" + ps.hashCode());
                    ps = null;
                }
            } catch (SQLException e) {
                Log.warning(e, () -> "PreparedStatement close error");
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Spliterator#tryAdvance(java.util.function.Consumer)
         */
        @Override
        public boolean tryAdvance(Consumer<? super ResultSet> action) {
            try {
                if (rs == null) {
                    rs = ps.executeQuery();
                    Log.config("ResultSet created #" + rs.hashCode());
                }
                if (rs.next()) {
                    if (action != null) {
                        action.accept(rs);
                    }
                    return true;
                }
            } catch (SQLException e) {
                Try.r(() -> rs.getStatement().getConnection().rollback()).run();
                throw new UncheckedSQLException(e);
            }
            close();
            return false;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Spliterator#trySplit()
         */
        @Override
        public Spliterator<ResultSet> trySplit() {
            return null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Spliterator#estimateSize()
         */
        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Spliterator#characteristics()
         */
        @Override
        public int characteristics() {
            return NONNULL | ORDERED;
        }

    }

    /**
     * SQL preparedQuery builder
     */
    public static class Query {

        /**
         * table
         */
        String table;
        /**
         * fields
         */
        List<String> fields;
        /**
         * conditions
         */
        List<String> wheres;
        /**
         * connection
         */
        private Db db;
        /**
         * orders
         */
        List<String> orders;
        /**
         * groups
         */
        List<String> groups;
        /**
         * conditions after grouping
         */
        List<String> havings;
        /**
         * offset
         */
        long offset = 0;
        /**
         * limit
         */
        long limit = 0;
        /**
         * row lock
         */
        boolean forUpdate = false;

        /**
         * constructor
         *
         * @param db database
         */
        protected Query(Db db) {
            this.db = db;
        }

        /**
         * from
         *
         * @param table table
         * @return preparedQuery
         */
        public Query from(String table) {
            this.table = table;
            return this;
        }

        /**
         * from
         *
         * @param table table
         * @return preparedQuery
         */
        public Query from(Class<?> table) {
            this.table = Reflector.mappingClassName(table);
            return this;
        }

        /**
         * use row lock
         *
         * @return preparedQuery
         */
        public Query forUpdate() {
            forUpdate = true;
            return this;
        }

        /**
         * grouping
         *
         * @param fields fields
         * @return preparedQuery
         */
        public Query groupBy(String... fields) {
            if (groups == null) {
                groups = new ArrayList<>();
            }
            for (String field : fields) {
                groups.add(field);
            }
            return this;
        }

        /**
         * orders(ascending)
         *
         * @param fields fields
         * @return preparedQuery
         */
        public Query orderBy(String... fields) {
            if (orders == null) {
                orders = new ArrayList<>();
            }
            for (String field : fields) {
                orders.add(field);
            }
            return this;
        }

        /**
         * condition after grouping
         *
         * @param condition condition
         * @return preparedQuery
         */
        public Query having(String condition) {
            if (havings == null) {
                havings = new ArrayList<>();
            }
            havings.add(condition);
            return this;
        }

        /**
         * orders(descending)
         *
         * @param fields fields
         * @return preparedQuery
         */
        public Query orderByDesc(String... fields) {
            if (orders == null) {
                orders = new ArrayList<>();
            }
            for (String field : fields) {
                orders.add(field + " DESC");
            }
            return this;
        }

        /**
         * condition
         *
         * @param condition condition
         * @return preparedQuery
         */
        public Query where(String condition) {
            if (wheres == null) {
                wheres = new ArrayList<>();
            }
            wheres.add(condition);
            return this;
        }

        /**
         * condition(equals)
         *
         * @param field field
         * @param value value
         * @return preparedQuery
         */
        public Query where(String field, Object value) {
            if (value == null) {
                return where(field + " IS NULL");
            }
            return where(field + " = " + db.builder.escape(value));
        }

        /**
         * condition(equals)
         *
         * @param field field
         * @param value value
         * @return preparedQuery
         */
        public Query where(Enum<?> field, Object value) {
            Class<?> clazz = Reflector.getGenericParameter(field.getClass().getDeclaringClass(), 0);
            String name = Reflector.mappingFieldName(Reflector.field(clazz, field.name()).get());
            if (value == null) {
                return where(name + " IS NULL");
            }
            return where(name + " = " + db.builder.escape(value));
        }

        /**
         * condition(binary operation)
         *
         * @param field field
         * @param operator operator
         * @param value value
         * @return preparedQuery
         */
        public Query where(String field, String operator, Object value) {
            if (value == null) {
                return where(field + " IS NULL");
            }
            return where(field + ' ' + operator + ' ' + db.builder.escape(value));
        }

        /**
         * set begin offset
         *
         * @param offset offset
         * @return preparedQuery
         */
        public Query offset(long offset) {
            this.offset = offset;
            return this;
        }

        /**
         * set max fetch count
         *
         * @param limit max fetch count
         * @return preparedQuery
         */
        public Query limit(long limit) {
            this.limit = limit;
            return this;
        }

        /**
         * build SQL
         *
         * @return SQL
         */
        public String sql() {
            return db.builder.sql(this);
        }

        /**
         * @return stream
         */
        public Stream<ResultSet> stream() {
            return db.stream(sql());
        }

        /**
         * check if row exists
         *
         * @return true: exists, false: not exists
         */
        public boolean exists() {
            try (Stream<ResultSet> stream = stream()) {
                return stream.findAny().isPresent();
            }
        }

        /**
         * get row count
         *
         * @return row count
         */
        public long count() {
            try (Stream<ResultSet> stream = db.stream(db.builder.countSql(sql()))) {
                return stream.findFirst().map(Try.f(rs -> rs.getLong(1))).orElse(0L);
            }
        }

        /**
         * get one value
         *
         * @param <T> value type
         * @param fetcher function that ResultSet to value
         * @return value
         */
        public <T> Optional<T> one(TryFunction<ResultSet, T> fetcher) {
            try (Stream<ResultSet> rows = stream()) {
                return rows.findFirst().map(Try.f(fetcher));
            }
        }

        /**
         * fetch first row
         *
         * @param fetcher fetcher
         * @return true: first row was existed, false: was not existed
         */
        public boolean row(TryConsumer<ResultSet> fetcher) {
            try (Stream<ResultSet> rows = stream()) {
                return rows.findFirst().map(rs -> {
                    Try.c(fetcher).accept(rs);
                    return true;
                }).orElse(false);
            }
        }

        /**
         * fetch rows
         *
         * @param fetcher fetcher
         * @return row count
         */
        public long rows(TryConsumer<ResultSet> fetcher) {
            try (Stream<ResultSet> rows = stream()) {
                return rows.peek(Try.c(fetcher)).count();
            }
        }

        /**
         * delete
         */
        public void delete() {
            String sql = db.builder.deleteSql(this);
            Log.info(sql + ";");
            try (PreparedStatement ps = db.connection.prepareStatement(sql)) {
                ps.execute();
            } catch (SQLException e) {
                Try.r(db.connection::rollback).run();
                throw new UncheckedSQLException(e);
            }
        }
    }

    /**
     * ? to value
     * 
     * @param sql SQL
     * @param values values
     * @return executable SQL
     */
    public String preparedSQL(String sql, Object... values) {
        Function<Object, String> cut = s -> Tool.cut((String) s, Sys.Log.parameter_max_letters);
        Function<Object, String> to = v -> v instanceof Collection
                ? ((Collection<?>) v).stream().map(cut).map(builder::escape).collect(Collectors.joining(", ", "[", "]")) : builder.escape(v);
        return Tool.trim(null, Tool.zip(Stream.of(sql.split("[?]")), Stream.concat(Stream.of(values).map(to), Stream.generate(() -> "?"))).map(t -> t.l + t.r)
                .collect(Collectors.joining()), "?");
    }

    /**
     * use PreparedStatement
     *
     * @param sql SQL
     * @param prepare prepare parameters
     */
    public void prepare(String sql, TryFunction<PreparedStatement, Object[]> prepare) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Object[] values = Try.f(prepare).apply(ps);
            if (values != null) {
                Log.info(() -> preparedSQL(sql, values));
            }
        } catch (SQLException e) {
            Try.r(connection::rollback).run();
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * use PreparedStatement and executeQuery
     *
     * @param sql SQL
     * @param prepare prepare parameters
     * @param fetch fetch row
     * @return count
     */
    public long preparedQuery(String sql, TryFunction<PreparedStatement, Object[]> prepare, TryConsumer<ResultSet> fetch) {
        long count = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Object[] values = Try.f(prepare).apply(ps);
            if (values != null) {
                Log.info(() -> preparedSQL(sql, values));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    count++;
                    Try.c(fetch).accept(rs);
                }
            }
        } catch (SQLException e) {
            Try.r(connection::rollback).run();
            throw new UncheckedSQLException(e);
        }
        return count;
    }

    /**
     * @param <T> taget class type
     * @param clazz target class
     * @return stream of target class instance
     */
    public <T> Stream<T> findAll(Class<T> clazz) {
        return from(Reflector.mappingClassName(clazz)).stream().map(Try.f(toObject(clazz)));
    }

    /**
     * @param <T> Model type
     * @param clazz Model class
     * @param targetColumns fill columns
     * @return found list
     */
    public <T> Stream<T> find(Class<T> clazz, String... targetColumns) {
        List<Field> fields;
        if (targetColumns == null || targetColumns.length <= 0) {
            targetColumns = Tool.array("*");
            fields = Reflector.mappingFields(clazz).values().stream().filter(f -> !Modifier.isTransient(f.getModifiers())).collect(Collectors.toList());
        } else {
            targetColumns = Stream.of(targetColumns).map(name -> Reflector.field(clazz, name).orElse(null)).map(Reflector::mappingFieldName)
                    .toArray(String[]::new);
            fields = Stream.of(targetColumns).map(s -> Reflector.mappingField(clazz, s)).filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toList());
        }
        List<Field> instanceFields = fields.stream().filter(f -> !Modifier.isStatic(f.getModifiers())).collect(Collectors.toList());
        if (Reflector.constructor(clazz).isPresent()) {
            return select(targetColumns).from(Reflector.mappingClassName(clazz)).stream().map(rs -> instanceFields.stream()
                    .collect(() -> Reflector.instance(clazz), Try.biC((o, f) -> f.set(o, resultSetToObject(f, rs, Reflector.mappingFieldName(f)))), (a, b) -> {
                    }));
        } else {
            return select(targetColumns).from(Reflector.mappingClassName(clazz)).stream()
                    .map(rs -> instanceFields.stream().<AbstractBuilder<T, ?, ?>>collect(() -> Factory.Constructor.instance(clazz),
                            Try.biC((b, f) -> b.set(f.getName(), resultSetToObject(f, rs, Reflector.mappingFieldName(f)))), (a, b) -> {
                            }).get());
        }
    }

    /**
     * @param <T> taget class type
     * @param clazz target class
     * @return object
     */
    public static <T> TryFunction<ResultSet, T> toObject(Class<T> clazz) {
        Map<String, Field> map = Reflector.fields(clazz);
        return rs -> {
            T object = Reflector.instance(clazz);
            ResultSetMetaData meta = rs.getMetaData();
            IntStream.rangeClosed(1, meta.getColumnCount()).mapToObj(Try.intF(meta::getColumnName)).forEach(name -> {
                Tool.of(map.get(name)).ifPresent(Try.c(field -> field.set(object, resultSetToObject(field, rs, name))));
            });
            return object;
        };
    }

    /**
     * db value to java value
     * 
     * @param <T> enum type
     * @param field field
     * @param rs ResultSet
     * @param name column name
     * @return java value
     * @throws SQLException SQL error
     */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> Object resultSetToObject(Field field, ResultSet rs, String name) throws SQLException {
        Join join = field.getAnnotation(Join.class);
        if (join != null) {
            return null;
        }
        Stringer stringer = field.getAnnotation(Stringer.class);
        if (stringer != null) {
            return Reflector.instance(stringer.value()).fromString(rs.getString(name));
        }
        Class<?> baseType = field.getType();
        boolean isOptional = baseType == Optional.class;
        Class<?> type = isOptional ? Reflector.getGenericParameter(field, 0) : baseType;
        Optional<Object> optional = Optional.empty();
        if (Enum.class.isAssignableFrom(type)) {
            if (IntSupplier.class.isAssignableFrom(type)) {
                int n = rs.getInt(name);
                optional = Stream.<Object>of(type.getEnumConstants()).filter(i -> ((IntSupplier) i).getAsInt() == n).findFirst();
            } else {
                optional = Optional.of(Enum.valueOf((Class<T>) type, rs.getString(name)));
            }
        }
        if (type == LocalDate.class) {
            optional = Tool.val(rs.getDate(name), v -> Tool.of(v).map(java.sql.Date::toLocalDate));
        }
        Object value = optional.orElseGet(Try.s(() -> rs.getObject(name), e -> null));
        return isOptional ? Tool.of(value) : value;
    }

    /**
     * @param model insert target
     * @param targetColumns save column names(primary key is automatic inclusion)
     * @return inserted rows
     */
    public int insert(Object model, String... targetColumns) {
        return modelAction(this::insert, model, targetColumns);
    }

    /**
     * @param model update target
     * @param targetColumns save column names(primary key is automatic inclusion)
     * @return updated rows
     */
    public int update(Object model, String... targetColumns) {
        return modelAction(this::update, model, targetColumns);
    }

    /**
     * @param model save target
     * @param targetColumns save column names(primary key is automatic inclusion)
     * @return true: inserted、 false: updated
     */
    public boolean save(Object model, String... targetColumns) {
        return modelAction(this::save, model, targetColumns);
    }

    /**
     * @param model delete target
     * @param targetColumns save column names(primary key is automatic inclusion)
     * @return deleted rows
     */
    public int delete(Object model, String... targetColumns) {
        return modelAction(this::delete, model, targetColumns);
    }

    /**
     * @param <T> model type
     * @param action action
     * @param model target model
     * @param targetColumns target column names(primary key is automatic inclusion)
     * @return action result
     */
    static <T> T modelAction(QuadFunction<String, String[], Integer, Object[], T> action, Object model, String... targetColumns) {
        Class<?> clazz = model.getClass();
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<String> keys = Reflector.fields(clazz).values().stream().filter(Reflector.hasAnnotation(Id.class)).map(Try.f(field -> {
            String name = Reflector.mappingFieldName(field);
            names.add(name);
            values.add(field.get(model));
            return name;
        })).collect(Collectors.toList());
        if (targetColumns == null || targetColumns.length <= 0) {
            Reflector.mappingFields(clazz).entrySet().stream().filter(t -> !keys.contains(t.getKey()))
                    .filter(t -> !Modifier.isTransient(t.getValue().getModifiers())).forEach(Try.c(t -> {
                        names.add(t.getKey());
                        values.add(t.getValue().get(model));
                    }));
        } else {
            for (String name : targetColumns) {
                Field field = Reflector.field(clazz, name).get();
                String mappingName = Reflector.mappingFieldName(field);
                if (keys.contains(mappingName)) {
                    continue;
                }
                names.add(mappingName);
                values.add(Try.s(() -> field.get(model)).get());
            }
        }
        return action.apply(Reflector.mappingClassName(model), names.toArray(new String[names.size()]), keys.size(), values.toArray());
    }

    /**
     * @param clazz target class
     * @return SQL
     */
    public String createSql(Class<?> clazz) {
        List<Column> primary = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        Reflector.mappingFields(clazz).forEach((name, field) -> {
            if (Modifier.isTransient(field.getModifiers())) {
                return;
            }
            if (Tool.of(field.getAnnotation(Join.class)).flatMap(join -> Tool.string(join.table())).isPresent()) {
                return;
            }
            Column c = new Column(name);
            c.type = field.getType();
            if (Optional.class.isAssignableFrom(c.type)) {
                c.type = Reflector.getGenericParameter(field, 0);
                c.nullable();
            }
            Tool.of(field.getAnnotation(Help.class)).ifPresent(help -> c.display(String.join(" ", help.value())));
            Tool.of(field.getAnnotation(Size.class)).ifPresent(size -> c.length(size.value()));
            Tool.of(field.getAnnotation(Id.class)).map(id -> primary).orElse(columns).add(c);
        });
        return createSql(Reflector.mappingClassName(clazz), primary.size(), Stream.concat(primary.stream(), columns.stream()).toArray(Column[]::new));
    }

    /**
     * @param clazz target class
     */
    public void create(Class<?> clazz) {
        execute(createSql(clazz), null);
    }

    /**
     * @param table Table
     * @param fields Fields(Comma separated)
     * @param values Values(Comma separated)
     */
    public void insert(String table, String fields, String[] values) {
        String prefix = "INSERT INTO " + table + Tool.string(fields).map(s -> "(" + s + ")").orElse("") + " VALUES(";
        Stream.of(values).map(value -> prefix + value + ")").forEach(sql -> execute(sql, null));
    }
}
