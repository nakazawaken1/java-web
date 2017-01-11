package framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
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

/**
 * self referable properties
 */
public enum Config {

    /**
     * log folder
     */
    log_folder("/temp/"),

    /**
     * log filename pattern(DateTimeFormatter format）
     */
    log_file_pattern("'ll_'yyyyMMdd'.log'"),

    /**
     * log line pattern
     */
    log_format("%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s [%2$s] %5$s %6$s%n"),

    /**
     * log level
     */
    log_level(Level.CONFIG),

    /**
     * class of suppress log
     */
    log_exclude("", ","),

    /**
     * database suffix
     */
    db_suffix,

    /**
     * datasource generator class
     */
    db_datasource_class,

    /**
     * database connection string
     */
    db_url,

    /**
     * database auto config(create: drop and create, [update]: create if not exists, reload: delete and insert, none: no operation)
     */
    db_setup(Db.Setup.UPDATE),

    /**
     * session timeout(seconds, indefinite if negative value)
     */
    app_session_timeout_seconds(60 * 30),

    /**
     * upload folder
     */
    app_upload_folder("/temp/"),

    /**
     * sql folder
     */
    app_sql_folder("sql/"),

    /**
     * add http response headers
     */
    app_headers("X-UA-Compatible: IE=edge|Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0|Expires: -1|Pragma: no-cache", "\\s*\\|\\s*"),

    /**
     * htdocs folder
     */
    app_view_folder("view/"),

    /**
     * template folder
     */
    app_template_folder("template/"),

    ;

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
    private static final AtomicBoolean initialized = new AtomicBoolean();

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
        this(defaultValue, ",");
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
        properties = new Properties();
        try (InputStream in = toURL(configFile).orElseThrow(() -> new IOException(configFile + " not found")).openStream(); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
            properties.putAll(System.getProperties());
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
            startupLog();
            Logger.getGlobal().info(Tool.print(ps -> {
                ps.println("-- listing config --");
                String suffix = db_suffix.get().orElse("");
                for (Config i : Config.values()) {
                    String key = i.toString();
                    ps.print(key + '=');
                    if (key.startsWith("db.") && i != db_suffix) {
                        ps.println(find(i + suffix).orElse(i.defaultValue));
                    } else {
                        ps.println(i.text());
                    }
                }
            }));
        } catch (IOException e) {
            Logger.getGlobal().warning(() -> e.toString());
        }
    }

    /* (non-Javadoc)
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
     * @param enumClass enum class
     * @return enum
     */
    public <T extends Enum<T>> T enumeration(Class<T> enumClass) {
        return (T)Enum.valueOf(enumClass, text());
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
                if (i instanceof ConsoleHandler) {
                    if (!(i.getFormatter() instanceof LogFormatter)) {
                        i.setFormatter(new LogFormatter(log_format.text()));
                        i.setLevel(level);
                    }
                }
                if (i instanceof LogHandler) {
                    noEntry = false;
                }
            }
            if (noEntry) {
                if (initialized.compareAndSet(false, true)) {
                    handler = new LogHandler(log_folder.text(), log_file_pattern.text());
                    handler.setLevel(level);
                    handler.setFormatter(new LogFormatter(log_format.text()));
                }
                root.addHandler(handler);
                Logger.getGlobal().info("addHandler: " + handler);
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
                Logger.getGlobal().info("removeHandler: " + i);
                i.close();
                root.removeHandler(i);
            }
        }
    }

    /**
     * @param args not use
     */
    public static void main(String[] args) {
        properties.entrySet().forEach(pair -> {
            System.out.println(pair.getKey() + " = " + pair.getValue());
        });
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
     * @param id id
     * @param exception exception generator
     * @return value
     * @throws E exception type
     */
    public static <E extends Throwable> String getOrThrow(String id, Function<String, E> exception) throws E {
        return find(id).orElseThrow(() -> exception.apply("config not found: " + id));
    }
}