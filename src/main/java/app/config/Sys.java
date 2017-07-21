package app.config;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

import app.controller.Main;
import app.model.Account;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Db.Setup;
import framework.Message;
import framework.Tool;
import framework.annotation.Config;
import framework.annotation.Help;
import framework.annotation.Mapping;
import framework.annotation.Separator;

/** not use primitive type because not apply property by optimized */
@SuppressWarnings("javadoc")
@SuppressFBWarnings("MS_SHOULD_BE_FINAL")
@Config({ "sys.config", "sys.message" })
public class Sys {

    public static class Log {
        @Help("log output folder")
        public static String folder = "/temp/";

        @Help("log filename pattern(DateTimeFormatter format, 'll' replace to level)")
        public static DateTimeFormatter file_pattern = Tool.getFormat("'ll_'uuuuMMdd'.log'", Locale.JAPAN);

        @Help("log line format(1: timestamp, 2: called method, 3: logger name, 4: level, 5: message, 6: exception, 7: request id, 8: session id, 9: application id)")
        public static String format = "%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-6s %9$08X|%8$08X|%7$08X [%3$s] %5$s %6$s%n";

        @Help("output log level")
        public static Level level = Level.CONFIG;

        @Help("classes of suppress log")
        @Separator(',')
        public static List<String> ignore_prefixes = Tool.list("com.sun.");

        @Help("shared lock if true else exclusive lock")
        public static boolean is_shared = true;

        @Help("eval log max output letters")
        public static int eval_max_letters = 100;

        @Help("request parameter max output letters")
        public static int parameter_max_letters = 50;
    }

    public static class Mail {
        @Help("SMTP Auth user")
        public static String user = "";
        @Help("SMTP Auth password")
        public static String password = "";
        @Help("SMTP host")
        public static String host = "smtp.gmail.com";
        @Help("ISO-2022-JP or UTF-8 in Japan")
        public static String charset = StandardCharsets.UTF_8.name();
        @Help("Mail encoding")
        public static String encoding = "base64";
        @Help("SMTP port")
        public static int port = 587;
        @Help("SMTP Auth enabled")
        public static boolean auth = true;
        @Help("SMTP Auth with tls")
        public static boolean startTls = true;
        @Help("Connection timeout")
        public static int connectionTimeout = 10 * 1000;
        @Help("Read timeout")
        public static int readTimeout = 10 * 1000;
        @Help("Debug output")
        public static boolean debug = true;
    }

    public static class Db {
        @Help("default database suffix")
        public static String suffix = "h2";

        @Help("datasource generator class")
        public static String datasource_class = "";

        @Help({ "database auto config", "CREATE: drop and create", "[UPDATE]: create if not exists", "RELOAD: delete and insert", "NONE: no operation" })
        public static Setup setup = Setup.UPDATE;

        @Help("session store db suffix")
        public static String session_suffix = suffix;

        @Help("h2 database connection string(inclucde id and password)")
        public static String h2 = "jdbc:h2:~/test";

        @Help("h2 tcp database connection string(inclucde id and password)")
        public static String h2tcp = "jdbc:h2:tcp://localhost/~/test";

        @Help("Oracle tcp database connection string(inclucde id and password)")
        public static String oracle = "jdbc:oracle:thin:system/manager@localhost:1521:orcl";

        @Help("MySQL tcp database connection string(inclucde id and password)")
        public static String mysql = "jdbc:mysql://localhost/test?user=root&password=&characterEncoding=utf8";

        @Help("Postgres tcp database connection string(inclucde id and password)")
        public static String postgres = "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=";

        @Help("SQLServer tcp database connection string(inclucde id and password)")
        public static String sqlserver = "jdbc:sqlserver://localhost:1433;user=sa;password=";
    }

    @Help("session cookie name")
    public static String session_name = "JavaWebSession";

    @Help("session timeout minutes(indefinite if negative value)")
    public static int session_timeout_minutes = 30;

    @Help("upload folder")
    public static String upload_folder = "/temp/";

    @Help("sql folder")
    public static String sql_folder = "/sql/";

    @Help({ "add http response headers", "X-UA-Compatible: IE=edge #IE version", "X-Content-Type-Options: nosniff #IE auto detect disabled",
            "X-Download-Options: noopen #IE direct open disabled",
            "Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0|Expires: -1|Pragma: no-cache # cache disabled" })
    @Separator(value = '|', pair = ':')
    public static Map<String, String> headers = Tool.map("X-UA-Compatible", "IE=edge", "Cache-Control",
            "no-store, no-cache, must-revalidate, post-check=0, pre-check=0", "X-Content-Type-Options", "nosniff", "X-Download-Options", "noopen", "Pragma",
            "no-cache", "Expires", "-1");

    @Help("htdocs folder")
    public static String document_root_folder = "/view/";

    @Help("template folder")
    public static String template_folder = "/template/";

    @Help("include file pattern to apply format")
    public static Pattern format_include_regex = Pattern.compile(".*\\.(html?|js|css)");

    @Help("exclude file pattern to apply format")
    public static Pattern format_exclude_regex = Pattern.compile(".*[.]min[.](html?|js|css)");

    @Help("scheduled job thread count")
    public static int job_threads = 1;

    @Help("login method: static java.util.Optional<framework.Account> ?(java.lang.String loginId, java.lang.String password)")
    public static String login_method = Account.class.getName() + ".loginWithConfig";

    @Help("accounts data(loginId:password:name:roles|...)")
    @Separator('|')
    public static List<String> accounts = Tool.list("admin:Adm1n:Administrator:Administrator:&#127877;", "user:U5er:User:User:&#128104;");

    @Help("file extension of text type contents")
    @Separator('|')
    public static List<String> text_extensions = Tool.list(".txt", ".htm", ".html", ".js", ".json", ".css", ".csv", ".tsv", ".xml", ".ini", ".yml",
            ".properties", ".php", ".java", ".jsp", ".xhtml");

    @Help("http port(disabled if empty)")
    @SuppressFBWarnings("MS_CANNOT_BE_FINAL")
    public static Optional<Integer> http_port = Tool.of(80);

    @Help("https port(disabled if empty, standard value is 443)")
    public static Optional<Integer> https_port = Tool.of();

    @Help("https private key(etc. host.key)")
    public static Optional<String> https_key_file = Tool.of();

    @Help("https cert(etc. host.crt)")
    public static List<String> https_cert_files = Tool.list();

    @Help("context path")
    public static String context_path = "/";

    @Help("h2 web interface port(disabled if empty)")
    public static Optional<Integer> h2_web_port = Tool.of();

    @Help("h2 web interface access allowed remote client")
    public static boolean h2_web_allow_remote = false;

    @Help("h2 web interface using https")
    public static boolean h2_web_ssl = false;

    @Help("h2 tcp server port(disabled if empty)")
    public static Optional<Integer> h2_tcp_port = Tool.of();

    @Help("h2 tcp server access allowed remote client")
    public static boolean h2_tcp_allow_remote = false;

    @Help("h2 tcp server using https")
    public static boolean h2_tcp_ssl = false;

    @Help("cluster node name suffix(for session cookie, It must be the same length in each cluster)")
    public static String cluster_suffix = "";

    @Help("job packages")
    public static List<String> job_packages = Tool.list(Main.class.getPackage().getName());

    @Help("separator of array to text")
    public static String array_separator = "\n,\n";

    @Help("prefix of array to text")
    public static String array_prefix = "[\n";

    @Help("suffix of array to text")
    public static String array_suffix = "\n]";

    @Help("Initial vector for Tool.enctypt and decrypt")
    public static String IV = "CYKJRWWIYWJHSLEU";

    @Help("Session store(db or redis)")
    public static Object session_store = "db";

    @Help("host if use redis")
    public static String session_redis_host = "127.0.0.1";

    @Help("port if use redis")
    public static int session_redis_port = 6379;

    @Help("redirect url if not loggged in(not redirect if empty)")
    public static Optional<String> redirect_if_not_login = Tool.of("/admin/login.html");

    @Help("Default pages")
    public static List<String> default_pages = Tool.list("index.html", "index.htm");

    @Help("Aliases if key file is not exists")
    public static Map<String, String> aliases = Tool.map("/", "/index.html", "/index.html", "/admin/index.html");

    @Help("Default avator")
    public static String default_avator = "&#9924;";

    @Help("Request method change parameter key")
    public static String request_method_key = "_method";

    @Help("Background css")
    public static String background = "#23282d";

    @Help("Nendo start month")
    public static int nendo_start_month = 4;

    public enum Item implements Message {
        title,
        login,
        logout,
        error,
        loginId,
        password,
        update,
        insert,
        delete,
        before,
        after,
        back,
        quit,
        diff,
        index,
        compact,
        full,
        @Mapping("©2017, All Rights Reserved.")
        copyright,
        @Mapping("...")
        reader,
        run,
        clear,
        config,
        hash,
        @Mapping("Database Settings")
        dbSettings,
        @Mapping("Database Console")
        dbConsole,
        @Mapping("Administrator menu")
        adminTitle,
        reset,
        @Mapping("Account list")
        accountList,
        yes,
        no,
        @Mapping("OK")
        ok,
        route,
        file;

        @Override
        public String toString() {
            return message();
        }
    }

    public enum Alert implements Message {
        @Mapping("You do not have access rights. Please login with authorized account.")
        forbidden,
        @Mapping("Login ID or password is wrong.")
        loginFailed,
        @Mapping("System error!")
        error,
        @Mapping("Please enter from ${min} to ${value} characters.")
        size,
        @Mapping("Input error!")
        inputError,
        @Mapping("Invalid characer")
        letters,
        @Mapping("Input required")
        required,
        @Mapping("Value is out of range")
        range,
        @Mapping("Time is out of range")
        time,;

        @Override
        public String toString() {
            return message();
        }
    }

    public enum Prompt implements Message {
        @Mapping("Please input user ID and password and press the login button.")
        login,;

        @Override
        public String toString() {
            return message();
        }
    }

    public enum Confirm implements Message {
        @Mapping("Do you want to log out?")
        logout,;

        @Override
        public String toString() {
            return message();
        }
    }
}
