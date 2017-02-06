package framework;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Try.TryConsumer;
import framework.Try.TryFunction;

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
        H2,

        /**
         * MySQL
         */
        MYSQL,

        /**
         * PostgreSQL
         */
        POSTGRESQL,

        /**
         * SQLServer
         */
        SQLSERVER,

        /**
         * Oracle
         */
        ORACLE;

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return super.toString().toLowerCase();
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
                logger.info("[tables]");
                db.tables().peek(logger::info).filter(table::equals).forEach(db::drop);
                String[] names = db.create(table, 1, new Column("id").integer(), new Column("name").text(10), new Column("birthday").date(),
                        new Column("weight").decimal(4, 1));
                for (int i = 1; i <= 20; i++) {
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DATE, -i * 31);
                    db.insert(table, names, 1, i, "氏名'" + i, c.getTime(), BigDecimal.valueOf(Math.random() * 80 + 40));
                }
                logger.info(table + " rows: " + db.from(table).count());
                Query q = db.select("name", "birthday", "weight").from(table).where(db.builder.fn("MONTH", "birthday") + " > 6").orderBy("id");
                logger.info("querey rows: " + q.count());
                TryConsumer<ResultSet> printer = row -> {
                    ResultSetMetaData meta = row.getMetaData();
                    IntStream.rangeClosed(1, meta.getColumnCount()).forEach(Try.intC(i -> System.out.println(meta.getColumnName(i) + "=" + row.getObject(i))));
                };
                logger.info("7月以降まれ[1-3]");
                q.limit(3).rows(printer);
                logger.info("7月以降まれ[4-6]");
                q.offset(3).rows(printer);
                logger.info("7月以降まれ[7-]");
                q.offset(6).limit(0).rows(printer);
                db.truncate(table);
                logger.info(table + " rows: " + db.from(table).count());
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
     * logger
     */
    transient private static Logger logger = Logger.getLogger(Db.class.getCanonicalName());

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
                Try.intC((map, i) -> map.put(meta.getColumnName(i), rs.getObject(i))), LinkedHashMap::putAll);
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
            Type type = Enum.valueOf(Type.class, Config.getOrThrow(Config.db_url + suffix, SQLException::new).split(":")[1].toUpperCase());
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
            if (port <= 0)
                port = 5432;
            break;
        case MYSQL:
            if (port <= 0)
                port = 3306;
            break;
        case ORACLE:
            pad = "thin:@";
            if (port <= 0)
                port = 1521;
            break;
        case SQLSERVER:
            pad2 = ";database=";
            if (port <= 0)
                port = 1433;
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
        logger.config(() -> "Connection created #" + connection.hashCode() + ", type = " + type);
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
            builder = new Builder() {

                @Override
                public String getVariablesSql() {
                    return "SELECT * FROM INFORMATION_SCHEMA.SETTINGS";
                }
            };
            break;
        case MYSQL:
            builder = new Builder() {

                @Override
                public String getVariablesSql() {
                    return "SHOW VARIABLES";
                }
            };
            break;
        }
    }

    /**
     * prepare data source
     * 
     * @param suffix Config.db_suffix
     * @return data source
     */
    public static synchronized DataSource getDataSource(String suffix) {
        return dataSourceMap.computeIfAbsent(suffix, key -> {
            try {
                Class<?> c = Class.forName(Config.getOrThrow(Config.db_datasource_class + key, RuntimeException::new));
                DataSource ds = (DataSource) c.newInstance();
                Config.find(Config.db_url + key).filter(Tool.notEmpty).ifPresent(Try.c(value -> {
                    for (String method : Tool.array("setURL", "setUrl")) {
                        try {
                            c.getMethod(method, String.class).invoke(ds, value);
                            break;
                        } catch (NoSuchMethodException e) {
                            continue;
                        }
                    }
                }));
                logger.info("DataSource created #" + ds);
                return ds;
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                logger.log(Level.WARNING, "cannot get dataSource", e);
                throw new RuntimeException(e);
            }
        });
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
            }
            connection.close();
            logger.config("Connection dropped #" + connection.hashCode());
            connection = null;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Connection close error", e);
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
        return Formatter.format(sql, Formatter::excludeForSql, builder::escape, map, values);
    }

    /**
     * query(single sql only)
     * 
     * {@code [example] db.query("SELECT name FROM account ORDER BY 1", null).map(rs -> rs.getString(1)).forEach(System.out::println);}
     * 
     * @param sql SQL
     * @param map name value map({key} -> value)
     * @param values values({0}, {1}...)
     * @return ResultSet stream
     */
    @SuppressFBWarnings("OBL_UNSATISFIED_OBLIGATION")
    public Stream<ResultSet> query(String sql, Map<String, Object> map, Object... values) {
        sql = sql(sql, map, values);
        logger.info(sql + ";");
        try {
            return stream(connection.prepareStatement(sql));
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * query form file(single sql only)
     * 
     * @param name SQL file(with extension)
     * @param map name value map({key} -> value)
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
     * @param map name value map({key} -> value)
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
                logger.config("PreparedStatement created #" + hash);
                logger.info(s + ";");
                total += ps.executeUpdate();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            } finally {
                logger.config("PreparedStatement dropped #" + hash);
            }
        }
        return total;
    }

    /**
     * execute from file(multi sql support)
     * 
     * @param name SQL file(with extension)
     * @param map name value map({key} -> value)
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
        String folder = Config.app_sql_folder.text();
        String path = folder + type + '/' + name;
        String commonPath = folder + name;
        Optional<URL> url = Config.toURL(path);
        if (!url.isPresent()) {
            url = Config.toURL(commonPath);
        }
        return url.map(Try.f(i -> builder.replace(new String(Files.readAllBytes(Paths.get(i.toURI())), StandardCharsets.UTF_8))));
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
        } else {
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
            if (first && primary == 1 && value == null) { /* auto id if primary key is single and null */
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
            ResultSetSpliterator i = new ResultSetSpliterator(connection.prepareStatement(sql));
            if (resources == null) {
                resources = new ArrayList<>();
            }
            resources.add(i);
            return StreamSupport.stream(i, false).onClose(i::close);
        } catch (SQLException e) {
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
        q.fields = Arrays.asList(fields);
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
     * create table
     * 
     * @param table table name
     * @param primary primary key columns
     * @param columns columns
     * @return column names
     */
    public String[] create(String table, int primary, Column... columns) {
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
            if (!(column.value == null || (!Tool.string(column.value).isPresent() && !builder.supportNullString))) {
                sql.append(" DEFAULT ").append(builder.escape(column.value));
            }
            Tool.string(column.display).map(j -> " COMMENT " + j).ifPresent(sql::append);
            pad = ", ";
        }
        if (primary > 0) {
            sql.append(join(", PRIMARY KEY(", Arrays.asList(names).subList(0, primary), ", ")).append(")");
        }
        execute(sql.append(")").toString(), null);
        return names;
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
        NONE,

        ;
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
        String folder = Config.app_sql_folder.text();
        String tablePrefix = "object.";
        String dataPrefix = "data.";
        String suffix = ".sql";
        List<String> all;
        try (Stream<String> files = Tool.getResources(folder)) {
            all = files.filter(file -> file.endsWith(suffix) && (file.startsWith(tablePrefix) || file.startsWith(dataPrefix))).peek(logger::info)
                    .collect(Collectors.toList());
        }

        try (Db db = Db.connect()) {
            db.getSQL("setup" + suffix).map(sql -> db.execute(sql, null));

            /* get table creation sql */
            List<String> tables = all.stream().filter(file -> file.startsWith(tablePrefix)).collect(Collectors.toList());

            /* get exists tables */
            Set<String> existTables = db.tables().collect(Collectors.toSet());

            /* table name getter */
            Function<String, String> tableName = file -> file.substring(tablePrefix.length(), file.length() - suffix.length());

            Set<String> reloadTables;
            if (create) {
                /* drop exists tables */
                tables.stream().map(tableName).filter(table -> existTables.contains(table)).forEach(table -> db.drop(table));
                /* create all tables and entry to load data */
                reloadTables = tables.stream().peek(file -> db.executeFile(file, null)).map(tableName).collect(Collectors.toSet());
            } else if (reload) {
                /* create no-exists tables */
                tables.stream().filter(file -> !existTables.contains(tableName.apply(file))).forEach(file -> db.executeFile(file, null));
                /* entry all tables to load data */
                reloadTables = tables.stream().map(tableName).collect(Collectors.toSet());
            } else {
                /* create no-exists table and entry to load data */
                reloadTables = tables.stream().filter(file -> !existTables.contains(tableName.apply(file))).peek(file -> db.executeFile(file, null))
                        .map(tableName).collect(Collectors.toSet());
            }

            /* data load */
            Function<String, String> dataName = file -> file.substring(dataPrefix.length(), file.length() - suffix.length());
            all.stream().filter(file -> file.startsWith(dataPrefix)).filter(file -> reloadTables.contains(dataName.apply(file)))
                    .peek(file -> db.truncate(dataName.apply(file))).forEach(file -> db.executeFile(file, null));
        }
    }

    /**
     * cleanup
     * 
     * @throws Exception exception
     */
    public static void cleanup() throws Exception {
        Class.forName("com.mysql.jdbc.AbandonedConnectionCleanupThread").getMethod("shutdown").invoke(null);
    }

    /**
     * sql builder for MySQL and H2
     */
    abstract public static class Builder {
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
            return function + "(" + join("", Arrays.asList(args), ", ") + ")";
        }

        /**
         * escape value
         * 
         * @param value value
         * @return escaped value
         */
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
            if (Date.class.isAssignableFrom(column.type)) {
                return "DATE";
            }
            if (BigDecimal.class.isAssignableFrom(column.type)) {
                return "DECIMAL(" + column.length + ", " + column.scale + ")";
            }
            if (Number.class.isAssignableFrom(column.type)) {
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

        /**
         * @return sql of variables
         */
        abstract public String getVariablesSql();
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
                if (q.limit > 0)
                    sql.append(" LIMIT ").append(q.limit);
                if (q.offset > 0)
                    sql.append(" OFFSET ").append(q.offset);
                if (q.forUpdate)
                    sql.append(" FOR UPDATE");
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
            switch (function.toUpperCase()) {
            case "YEAR":
            case "MONTH":
            case "DAY":
                return "DATE_PART('" + function + "'" + join(", ", Arrays.asList(args), ", ") + ")";
            }
            return function + "(" + join("", Arrays.asList(args), ", ") + ")";
        }

        @Override
        public String getVariablesSql() {
            return "SHOW ALL";
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
            if (q.limit > 0 && q.offset <= 0)
                sql.append("TOP ").append(q.limit).append(" ");
            sql.append(q.fields == null ? "*" : join("", q.fields, ", "));
            String orderBy = join(" ORDER BY ", q.orders, ", ");
            boolean range = q.offset > 0;
            if (range) {
                String select = sql.toString();
                if (orderBy.isEmpty())
                    orderBy = " ORDER BY " + (q.fields == null ? "id" : q.fields.get(0));
                sql.append(" FROM (").append(select).append(", ROW_NUMBER()");
                if (!orderBy.isEmpty())
                    sql.append(" OVER(" + orderBy.substring(1) + ")");
                sql.append(" N__");
            }
            if (q.table != null) {
                sql.append(" FROM ").append(q.table);
                if (q.forUpdate)
                    sql.append(" WITH (UPDLOCK)");
                sql.append(join(" WHERE ", q.wheres, " AND "));
                sql.append(join(" GROUP BY ", q.groups, ", "));
                if (range) {
                    sql.append(") T__ WHERE N__");
                    if (q.limit <= 0)
                        sql.append(" > ").append(q.offset);
                    else
                        sql.append(" BETWEEN ").append(q.offset + 1).append(" AND ").append(q.offset + q.limit);
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
        public String escape(Object value) {
            if (value != null && value instanceof Date)
                return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value) + "'";
            if (value instanceof LocalDateTime || value instanceof ZonedDateTime || value instanceof OffsetDateTime)
                return "'" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format((TemporalAccessor) value) + "'";
            if (value instanceof LocalDate)
                return "'" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format((TemporalAccessor) value) + "'";
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
            if (orderBy > 0 && sql.indexOf(" TOP ") < 0)
                sql = sql.substring(0, orderBy);
            return "SELECT COUNT(*) FROM (" + sql + ") T__";
        }

        @Override
        public String getVariablesSql() {
            return "SELECT configuration_id, name, CONVERT(VARCHAR(1000), value) value, CONVERT(VARCHAR(1000), minimum) minimum, CONVERT(VARCHAR(1000), maximum) maximum, CONVERT(VARCHAR(1000), value_in_use) value_in_use, description, is_dynamic, is_advanced FROM sys.configurations";
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
            switch (function.toUpperCase()) {
            case "NOW":
                return "SYSDATE";
            case "YEAR":
            case "MONTH":
            case "DAY":
                return "EXTRACT(" + function + " FROM " + join("", Arrays.asList(args), ", ") + ")";
            }
            return function + "(" + join("", Arrays.asList(args), ", ") + ")";
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

        @Override
        public String getVariablesSql() {
            return "SELECT * FROM V$PARAMETER";
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
            logger.config("ResultSet created #" + rs.hashCode());
        }

        /**
         * constructor
         * 
         * @param ps prepared statement
         */
        public ResultSetSpliterator(PreparedStatement ps) {
            this.ps = ps;
            logger.config("PreparedStatement created #" + ps.hashCode());
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
                    logger.config("ResultSet dropped #" + rs.hashCode());
                    rs = null;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "ResultSet close error", e);
            }
            try {
                if (ps != null) {
                    ps.close();
                    logger.config("PreparedStatement dropped #" + ps.hashCode());
                    ps = null;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "PreparedStatement close error", e);
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
                    logger.config("ResultSet created #" + rs.hashCode());
                }
                if (rs.next()) {
                    if (action != null) {
                        action.accept(rs);
                    }
                    return true;
                }
            } catch (SQLException e) {
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
     * SQL query builder
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
         * @return query
         */
        public Query from(String table) {
            this.table = table;
            return this;
        }

        /**
         * use row lock
         * 
         * @return query
         */
        public Query forUpdate() {
            forUpdate = true;
            return this;
        }

        /**
         * grouping
         * 
         * @param fields fields
         * @return query
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
         * @return query
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
         * @return query
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
         * @return query
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
         * @return query
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
         * @return query
         */
        public Query where(String field, Object value) {
            if (value == null) {
                return where(field + " IS NULL");
            }
            return where(field + " = " + db.builder.escape(value));
        }

        /**
         * condition(binary operation)
         * 
         * @param field field
         * @param operator operator
         * @param value value
         * @return query
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
         * @return query
         */
        public Query offset(long offset) {
            this.offset = offset;
            return this;
        }

        /**
         * set max fetch count
         * 
         * @param limit max fetch count
         * @return query
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
         * check if row exists
         * 
         * @return true: exists, false: not exists
         */
        public boolean exists() {
            String sql = sql();
            logger.info(sql + ";");
            try (Stream<ResultSet> stream = db.stream(sql)) {
                return stream.findAny().isPresent();
            }
        }

        /**
         * get row count
         * 
         * @return row count
         */
        public long count() {
            String sql = db.builder.countSql(sql());
            logger.info(sql + ";");
            try (Stream<ResultSet> stream = db.stream(sql)) {
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
        public <T> Optional<T> one(Function<ResultSet, T> fetcher) {
            try (Stream<ResultSet> rows = rows()) {
                return rows.findFirst().map(fetcher);
            }
        }

        /**
         * get first row
         * 
         * @return Optional ResultSet
         */
        public Optional<ResultSet> row() {
            return rows().findFirst();
        }

        /**
         * fetch first row
         * 
         * @param fetcher fetcher
         * @return true: first row was existed, false: was not existed
         */
        public boolean row(TryConsumer<ResultSet> fetcher) {
            try (Stream<ResultSet> rows = rows()) {
                return rows.findFirst().map(rs -> {
                    Try.c(fetcher).accept(rs);
                    return true;
                }).orElse(false);
            }
        }

        /**
         * get rows
         * 
         * @return ResultSet stream
         */
        public Stream<ResultSet> rows() {
            String sql = sql();
            logger.info(sql + ";");
            return db.stream(sql);
        }

        /**
         * fetch rows
         * 
         * @param fetcher fetcher
         * @return row count
         */
        public long rows(TryConsumer<ResultSet> fetcher) {
            try (Stream<ResultSet> rows = rows()) {
                return rows.peek(Try.c(fetcher)).count();
            }
        }

        /**
         * execute deletion
         */
        public void delete() {
            String sql = db.builder.deleteSql(this);
            logger.info(sql + ";");
            try (PreparedStatement ps = db.connection.prepareStatement(sql)) {
                ps.execute();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        }
    }

    /**
     * use PreparedStatement
     * 
     * @param sql SQL
     * @param prepared after prepared action
     */
    public void prepare(String sql, TryFunction<PreparedStatement, Object[]> prepared) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for(Object i : Try.f(prepared).apply(ps)) {
                sql = sql.replaceFirst("\\?", builder.escape(i));
            }
            logger.info(sql);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * @param model find target
     * @param targetFields condition fields
     * @return found list
     */
    public <T> Stream<T> find(T model, String... targetFields) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param model insert target
     * @param targetFields save fields(primary key is automatic inclusion)
     */
    public void insert(Object model, String... targetFields) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param model update target
     * @param targetFields save fields(primary key is automatic inclusion)
     */
    public void update(Object model, String... targetFields) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param model save target
     * @param targetFields save fields(primary key is automatic inclusion)
     */
    public void save(Object model, String... targetFields) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param model delete target
     * @param keyFields key fields
     */
    public void delete(Object model, String... keyFields) {
        throw new UnsupportedOperationException();
    }
}
