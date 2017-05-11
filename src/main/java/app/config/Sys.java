package app.config;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
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
        public static List<String> ignore_prefixes = Collections.unmodifiableList(Arrays.asList("com.sun."));

        @Help("shared lock if true else exclusive lock")
        public static boolean is_shared = true;

        @Help("eval log max output letters")
        public static int eval_max_letters = 100;

        @Help("request parameter max output letters")
        public static int parameter_max_letters = 50;
    }

    public static class Mail {
        public static String user = "";
        public static String password = "";
        public static String host = "smtp.gmail.com";
        @Help("ISO-2022-JP or UTF-8 in Japan")
        public static String charset = StandardCharsets.UTF_8.name();
        public static String encoding = "base64";
        public static Integer port = 587;
        public static Boolean auth = true;
        public static Boolean startTls = true;
        public static Integer connectionTimeout = 10 * 1000;
        public static Integer readTimeout = 10 * 1000;
        public static Boolean debug = true;
    }

    @Help("database connection string(inclucde id and password)")
    public static String db = "jdbc:h2:~/test";

    public static class Db {
        @Help("default database suffix")
        public static String suffix = "";

        @Help("datasource generator class")
        public static String datasource_class = "";

        @Help({ "database auto config", "CREATE: drop and create", "[UPDATE]: create if not exists", "RELOAD: delete and insert", "NONE: no operation" })
        public static Setup setup = Setup.UPDATE;
    }

    @Help("session cookie name")
    public static String session_name = "JavaWebSession";

    @Help("session timeout minutes(indefinite if negative value)")
    public static int session_timeout_minutes = 30;

    @Help("upload folder")
    public static String upload_folder = "/temp/";

    @Help("sql folder")
    public static String sql_folder = "sql/";

    @Help({ "add http response headers", "X-UA-Compatible: IE=edge #IE version", "X-Content-Type-Options: nosniff #IE auto detect disabled",
            "X-Download-Options: noopen #IE direct open disabled",
            "Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0|Expires: -1|Pragma: no-cache # cache disabled" })
    @Separator(value = '|', pair = ':')
    public static Map<String, String> headers = Tool.map("X-UA-Compatible", "IE=edge", "Cache-Control",
            "no-store, no-cache, must-revalidate, post-check=0, pre-check=0", "X-Content-Type-Options", "nosniff", "X-Download-Options", "noopen", "Pragma",
            "no-cache", "Expires", "-1");

    @Help("htdocs folder")
    public static String document_root_folder = "view/";

    @Help("template folder")
    public static String template_folder = "template/";

    @Help("include file pattern to apply format")
    public static Pattern format_include_regex = Pattern.compile(".*\\.(html?|js|css)");

    @Help("exclude file pattern to apply format")
    public static Pattern format_exclude_regex = Pattern.compile(".*jquery.*\\.js");

    @Help("scheduled job thread count")
    public static int job_threads = 1;

    @Help("login method(must to be static method)")
    public static String login_method = Account.class.getName() + ".loginWithConfig";

    @Help("accounts data(loginId:password:name:roles,...)")
    @Separator(value = ';')
    public static List<String> accounts = Collections.unmodifiableList(Arrays.asList("admin:Adm1n:Administrator:Administrator"));

    @Help("file extension of text type contents")
    @Separator('|')
    public static List<String> text_extensions = Collections.unmodifiableList(Arrays.asList(".txt", ".htm", ".html", ".js", ".json", ".css", ".csv", ".tsv",
            ".xml", ".ini", ".yml", ".properties", ".php", ".java", ".jsp", ".xhtml"));

    @Help("http port(disabled if empty)")
    public static Optional<Integer> http_port = Optional.of(80);

    @Help("https port(disabled if empty, standard value is 443)")
    public static Optional<Integer> https_port = Optional.empty();

    @Help("https private key(etc. host.key)")
    public static Optional<String> https_key_file = Optional.empty();

    @Help("https cert(etc. host.crt)")
    public static List<String> https_cert_files = Collections.emptyList();

    @Help("context path")
    public static String context_path = "/";

    @Help("h2 web console port(disabled if empty)")
    public static Optional<Integer> h2_port = Optional.empty();

    @Help("h2 web console access allowed remote client")
    public static boolean h2_allow_remote = false;

    @Help("h2 web console using https")
    public static boolean h2_ssl = false;

    @Help("cluster node name suffix(for session cookie, It must be the same length in each cluster)")
    public static String cluster_suffix = "";

    @Help("job packages")
    public static List<String> job_packages = Collections.unmodifiableList(Arrays.asList(Main.class.getPackage().getName()));

    public enum Item implements Message {
        title,
        login,
        logout,
        error,
        login_id,
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
        @Mapping("©2016, All Rights Reserved.")
        copyright,
        @Mapping("...")
        reader,
        run,
        clear,;

        @Override
        public String toString() {
            return message();
        }
    }

    public enum Alert implements Message {
        @Mapping("You do not have access rights. Please login with authorized account.")
        forbidden,
        @Mapping("Login ID or password is wrong.")
        login_failed,;

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
}
