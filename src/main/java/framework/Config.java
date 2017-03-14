package framework;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.annotation.Help;

/**
 * self referable properties
 */
public enum Config {

    /**
     * log folder
     */
    @Help("log output folder")
    log_folder("/temp/"),

    /**
     * log filename pattern(DateTimeFormatter format, 'll' replace to level)
     */
    @Help("log filename pattern(DateTimeFormatter format, 'll' replace to level)")
    log_file_pattern("'ll_'yyyyMMdd'.log'"),

    /**
     * log line format
     */
    @Help("log line format")
    log_format("%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %3$s [%2$s] %5$s %6$s%n"),

    /**
     * output log level
     */
    @Help("output log level")
    log_level(Level.CONFIG),

    /**
     * class of suppress log
     */
    @Help("classes of suppress log")
    log_exclude("", ","),

    /**
     * database suffix
     */
    @Help("default database suffix")
    db_suffix,

    /**
     * datasource generator class
     */
    @Help("datasource generator class")
    db_datasource_class,

    /**
     * database connection string(inclucde id and password)
     */
    @Help("database connection string(inclucde id and password)")
    db("jdbc:h2:~/test"),

    /**
     * database auto config(create: drop and create, [update]: create if not exists, reload: delete and insert, none: no operation)
     */
    @Help({ "database auto config", "CREATE: drop and create", "[UPDATE]: create if not exists", "RELOAD: delete and insert", "NONE: no operation" })
    db_setup(Db.Setup.UPDATE),

    /**
     * database suffix for session
     */
    @Help("database suffix for session")
    db_suffix_session,

    /**
     * session cookie name
     */
    @Help("session cookie name")
    app_session_name("JavaWebSession"),

    /**
     * session timeout(minutes, indefinite if negative value)
     */
    @Help("session timeout minutes(indefinite if negative value)")
    app_session_timeout_minutes(30),

    /**
     * upload folder
     */
    @Help("upload folder")
    app_upload_folder,

    /**
     * sql folder
     */
    @Help("sql folder")
    app_sql_folder("sql/"),

    /**
     * add http response headers
     */
    @Help({ "add http response headers", "X-UA-Compatible: IE=edge #IE version", "X-Content-Type-Options: nosniff #IE auto detect disabled", "X-Download-Options: noopen #IE direct open disabled", "Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0|Expires: -1|Pragma: no-cache # cache disabled" })
    app_headers("X-UA-Compatible: IE=edge|X-Content-Type-Options: nosniff|X-Download-Options: noopen|Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0|Expires: -1|Pragma: no-cache", "\\s*\\|\\s*"),

    /**
     * htdocs folder
     */
    @Help("htdocs folder")
    app_view_folder("view/"),

    /**
     * template folder
     */
    @Help("template folder")
    app_template_folder("template/"),

    /**
     * include file pattern to apply format
     */
    @Help("include file pattern to apply format")
    app_format_include_regex(".*\\.(html?|js|css)"),

    /**
     * exclude file pattern to apply format
     */
    @Help("exclude file pattern to apply format")
    app_format_exclude_regex("jquery.*\\.js"),

    /**
     * scheduled job thread count
     */
    @Help("scheduled job thread count")
    app_job_threads(1),

    /**
     * login method(must to be static method)
     */
    @Help("login method(must to be static method)")
    app_login_method("framework.Account.loginWithConfig"),

    /**
     * accounts data(loginId:password:name:roles,...)
     */
    @Help("accounts data(loginId:password:name:roles,...)")
    app_accounts("admin:Adm1n:Administrator:Administrator"),

    /**
     * file extension of text type contents
     */
    @Help("file extension of text type contents")
    app_text_extensions(".txt|.htm|.html|.js|.json|.css|.csv|.tsv|.xml|.ini|.yml|.properties|.php|.java|.jsp|.xhtml", "\\s*\\|\\s*"),

    /**
     * http port(disabled if negative)
     */
    @Help("http port(disabled if negative)")
    app_http_port(80),

    /**
     * https port(disabled if negative, standard value is 443)
     */
    @Help("https port(disabled if negative, standard value is 443)")
    app_https_port(-1),

    /**
     * https private key
     */
    @Help("https private key(etc. host.key)")
    app_https_key_file,

    /**
     * https cert
     */
    @Help("https cert(etc. host.crt)")
    app_https_cert_files,

    /**
     * context path
     */
    @Help("context path")
    app_context_path("/"),

    /**
     * h2 web console port
     */
    @Help("h2 web console port(disabled if negative)")
    app_h2_port(-1),

    /**
     * h2 web console access limited local only
     */
    @Help("h2 web console access allowed remote client")
    app_h2_allow_remote(false),

    /**
     * h2 web console using https
     */
    @Help("h2 web console using https")
    app_h2_ssl(false),

    /**
     * cluster node name suffix(for session cookie)
     */
    @Help("cluster node name suffix(for session cookie)")
    app_cluster_suffix,

    /**
     * controller packages
     */
    @Help("controller packages")
    app_controller_packages("app.controller,app.job"),

    /**
     * job packages
     */
    @Help("job packages")
    app_job_packages("app.job"),

    /**
     * model packages
     */
    @Help("model packages")
    app_model_packages("app.model"),;

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Config.class.getCanonicalName());

    /**
     * variable bracket beginning mark
     */
    public static final String BEGIN = "${";

    /**
     * variable bracket ending mark
     */
    public static final String END = "}";

    /**
     * properties
     */
    static final Properties properties;

    /**
     * log handler
     */
    private static volatile Handler handler;

    /**
     * for initialize
     */
    private static final AtomicBoolean first = new AtomicBoolean(true);

    /**
     * default value
     */
    public final String defaultValue;

    /**
     * separator
     */
    public final String separator;

    /**
     * Constructor
     *
     * @param defaultValue default value
     * @param separator separator
     */
    private Config(Object defaultValue, String separator) {
        this.defaultValue = Tool.string(defaultValue).orElse("");
        this.separator = separator;
    }

    /**
     * Constructor
     *
     * @param defaultValue default value
     */
    private Config(Object defaultValue) {
        this(defaultValue, "\\s*,\\s*");
    }

    /**
     * Constructor
     */
    private Config() {
        this("");
    }

    /**
     * config file name
     */
    public static final String configFile = "config.txt";

    /**
     * load config
     */
    static {
        /* set default properties */
        properties = new Properties();
        Set<String> dbKeys = new HashSet<>();
        for (Config i : Config.values()) {
            String key = i.toString();
            if (key.startsWith("db") && i != db_suffix) {
                dbKeys.add(key);
            }
            properties.setProperty(key, i.text());
        }

        /* overwrite file properties */
        try (Reader reader = Tool.newReader(toURL(configFile).orElseThrow(() -> new IOException(configFile + " not found")).openStream())) {
            properties.load(reader);
        } catch (IOException e) {
            Logger.getGlobal().warning(() -> e.toString());
        }

        /* overwrite system properties(prefix "app db log" only) */
        System.getProperties().forEach((k, v) -> {
            String key = (String) k;
            if (Stream.of("app", "db", "log").anyMatch(key::startsWith)) {
                properties.setProperty(key, (String) v);
            }
        });

        /* overwrite dynamic properties */
        Map<String, String> backup = dbKeys.stream().filter(i -> Config.find(i).isPresent()).collect(Collectors.toMap(i -> i, properties::getProperty));
        db_suffix.get().ifPresent(suffix -> {
            for (String key : dbKeys) {
                find(key + suffix).ifPresent(value -> properties.setProperty(key, value));
            }
        });
        backup.forEach((key, value) -> properties.setProperty(key, value));

        /* resolve variables */
        for (;;) {
            boolean[] loop = { false };
            Set<String> missings = new LinkedHashSet<>();
            properties.entrySet().forEach(pair -> {
                resolve((String) pair.getValue(), properties, value -> {
                    properties.setProperty((String) pair.getKey(), value);
                    loop[0] = true;
                }, missings::add);
            });
            if (!loop[0]) {
                missings.stream().map(key -> BEGIN + key + END + " cannot resolve.").forEach(logger::warning);
                break;
            }
        }

        /* dump */
        startupLog();
        Logger.getGlobal().info(Tool.print(writer -> {
            writer.println("---- config ----");
            properties.entrySet().stream().sorted((a, b) -> ((String) a.getKey()).compareToIgnoreCase((String) b.getKey()))
                    .forEach(i -> writer.println(i.getKey() + "=" + i.getValue()));
        }));
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return super.toString().replace('_', '.');
    }

    /**
     * get config value
     *
     * @param id config id
     * @return config value
     */
    public static Optional<String> find(String id) {
        return Tool.string(properties.getProperty(id));
    }

    /**
     * @param relativePath relative path
     * @return url
     */
    public static Optional<URL> toURL(String... relativePath) {
        String path = Stream.of(relativePath).map(i -> Tool.trim("/", i, "/")).collect(Collectors.joining("/"));
        return Optional.ofNullable(Thread.currentThread().getContextClassLoader().getResource(path));
    }

    /**
     * get config value
     *
     * @return config value
     */
    public Optional<String> get() {
        return find(toString());
    }

    /**
     * get text
     *
     * @return text
     */
    public String text() {
        return get().orElse(defaultValue);
    }

    /**
     * get integer
     *
     * @return integer
     */
    public int integer() {
        try {
            return Integer.parseInt(text());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * get stream
     *
     * @return stream
     */
    public Stream<String> stream() {
        return Stream.of(text().split(separator)).filter(Tool.notEmpty);
    }

    /**
     * @param <T> enum type
     * @param enumClass enum type
     * @return enum
     */
    public <T extends Enum<T>> T enumOf(Class<T> enumClass) {
        return (T) Enum.valueOf(enumClass, text().toUpperCase());
    }

    /**
     * @return true if text is true or 1 or yes or on(ignore case)
     */
    public boolean isTrue() {
        return Arrays.asList("TRUE", "1", "YES", "ON").contains(text().toUpperCase());
    }

    /**
     * log initialize
     */
    public static void startupLog() {
        try {
            log_exclude.stream().forEach(i -> Logger.getLogger(i).setLevel(Level.SEVERE));
            Logger root = Logger.getLogger("");
            Level level = Level.parse(log_level.text().toUpperCase());
            root.setLevel(level);
            boolean noEntry = true;
            for (Handler i : root.getHandlers()) {
                if (i instanceof ConsoleHandler && !(i.getFormatter() instanceof LogFormatter)) {
                    i.setFormatter(new LogFormatter(log_format.text()));
                    i.setLevel(level);
                }
                if (i instanceof LogHandler) {
                    noEntry = false;
                }
            }
            if (noEntry) {
                if (first.compareAndSet(true, false)) {
                    handler = new LogHandler(log_folder.text(), log_file_pattern.text());
                    handler.setLevel(level);
                    handler.setFormatter(new LogFormatter(log_format.text()));
                }
                root.addHandler(handler);
                Logger.getGlobal().config("addHandler: " + handler);
            }
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * log finalize
     */
    public static void shutdownLog() {
        Logger root = Logger.getLogger("");
        for (Handler i : root.getHandlers()) {
            if (i instanceof LogHandler) {
                Logger.getGlobal().config("removeHandler: " + i);
                i.close();
                root.removeHandler(i);
            }
        }
    }

    /**
     * @param args not use
     */
    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public static void main(String[] args) {
        printDefault(new PrintWriter(System.out));
        // properties.entrySet().forEach(pair -> {
        // System.out.println(pair.getKey() + " = " + pair.getValue());
        // });
    }

    /**
     * resolve variable
     *
     * @param value value
     * @param source variables
     * @param changed changed action
     * @param missing missing action
     */
    static void resolve(String value, Properties source, Consumer<String> changed, Consumer<String> missing) {
        boolean isChanged = false;
        for (;;) {
            int start = value.indexOf(BEGIN);
            if (start < 0) {
                break;
            }
            start += BEGIN.length();
            int end = value.indexOf(END, start);
            if (end < 0) {
                break;
            }
            String key = value.substring(start, end);
            if (!source.containsKey(key)) {
                if (missing != null) {
                    missing.accept(key);
                }
                break;
            }
            String replace = source.getProperty(key);
            int loop = replace.indexOf(BEGIN);
            if (loop >= 0 && loop < replace.indexOf(END)) {
                if (missing != null) {
                    missing.accept(key);
                }
                break;
            }
            isChanged = true;
            value = value.substring(0, start - BEGIN.length()) + replace + value.substring(end + END.length());
        }
        if (isChanged) {
            changed.accept(value);
        }
    }

    /**
     * @param <E> exception type
     * @param id id
     * @param exception exception generator
     * @return value
     * @throws E exception type
     */
    public static <E extends Throwable> String getOrThrow(String id, Function<String, E> exception) throws E {
        return find(id).orElseThrow(() -> exception.apply("config not found: " + id));
    }

    /**
     * create default setting file
     * 
     * @param writer writer
     */
    public static void printDefault(PrintWriter writer) {
        Stream.of(Config.values()).sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString())).forEach(Try.c(i -> {
            Optional.ofNullable(i.getClass().getField(i.name()).getAnnotation(Help.class)).map(Help::value).map(Stream::of).orElse(Stream.empty())
                    .map(j -> "# " + j).forEach(writer::println);
            writer.println(i.toString() + " = " + i.defaultValue.replace("\\", "\\\\"));
            writer.println();
        }));
    }

    /**
     * create default setting file
     * 
     * @param writer writer
     */
    public static void printCurrent(PrintWriter writer) {
        properties.entrySet().stream().sorted((a, b) -> ((String) a.getKey()).compareToIgnoreCase((String) b.getKey())).forEach(i -> {
            try {
                Optional.ofNullable(Config.class.getField(((String) i.getKey()).replace('.', '_')).getAnnotation(Help.class)).map(Help::value).map(Stream::of)
                        .orElse(Stream.empty()).map(j -> "# " + j).forEach(writer::println);
            } catch (NoSuchFieldException | SecurityException e) {
                logger.info(i.toString());
            }
            writer.println(i.getKey() + " = " + ((String) i.getValue()).replace("\\", "\\\\"));
            writer.println();
        });
    }
}
