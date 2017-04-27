package app.config;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

import app.model.Account;
import framework.Db.Setup;
import framework.Tool;
import framework.annotation.Config;
import framework.annotation.Help;
import framework.annotation.Separator;

/** not use primitive type because not apply property by optimized */
@SuppressWarnings("javadoc")
@Config({ "sys.config", "sys.message" })
public interface Sys {

    public interface Log {
        @Help("log output folder")
        String folder = "/temp/";

        @Help("log filename pattern(DateTimeFormatter format, 'll' replace to level)")
        DateTimeFormatter file_pattern = DateTimeFormatter.ofPattern("'ll_'yyyyMMdd'.log'");

        @Help("log line format(1: timestamp, 2: called method, 3: logger name, 4: level, 5: message, 6: exception, 7: request id, 8: session id, 9: application id)")
        String format = "%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-6s %9$08X|%8$08X|%7$08X [%3$s] %5$s %6$s%n";

        @Help("output log level")
        Level level = Level.CONFIG;

        @Help("classes of suppress log")
        @Separator(',')
        List<String> ignore_prefixes = Collections.unmodifiableList(Arrays.asList("com.sun."));

        @Help("shared lock if true else exclusive lock")
        Boolean is_shared = true;

        @Help("eval log max output letters")
        Integer eval_max_letters = 100;

        @Help("request parameter max output letters")
        Integer parameter_max_letters = 50;
    }

    @Help("database connection string(inclucde id and password)")
    String db = "jdbc:h2:~/test";

    public interface Db {
        @Help("default database suffix")
        String suffix = "";

        @Help("datasource generator class")
        String datasource_class = "";

        @Help({ "database auto config", "CREATE: drop and create", "[UPDATE]: create if not exists", "RELOAD: delete and insert", "NONE: no operation" })
        Setup setup = Setup.UPDATE;
    }

    @Help("session cookie name")
    String session_name = "JavaWebSession";

    @Help("session timeout minutes(indefinite if negative value)")
    Integer session_timeout_minutes = 30;

    @Help("upload folder")
    String upload_folder = "/temp/";

    @Help("sql folder")
    String sql_folder = "sql/";

    @Help({ "add http response headers", "X-UA-Compatible: IE=edge #IE version", "X-Content-Type-Options: nosniff #IE auto detect disabled",
            "X-Download-Options: noopen #IE direct open disabled",
            "Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0|Expires: -1|Pragma: no-cache # cache disabled" })
    @Separator(value = '|', pair = ':')
    Map<String, String> headers = Tool.map("X-UA-Compatible", "IE=edge", "X-Content-Type-Options", "nosniff", "X-Download-Options", "noopen", "Cache-Control",
            "no-store, no-cache, must-revalidate, post-check=0, pre-check=0", "Expires", "-1", "Pragma", "no-cache");

    @Help("htdocs folder")
    String document_root_folder = "view/";

    @Help("template folder")
    String template_folder = "template/";

    @Help("include file pattern to apply format")
    Pattern format_include_regex = Pattern.compile(".*\\.(html?|js|css)");

    @Help("exclude file pattern to apply format")
    Pattern format_exclude_regex = Pattern.compile(".*jquery.*\\.js");

    @Help("scheduled job thread count")
    Integer job_threads = 1;

    @Help("login method(must to be static method)")
    String login_method = Account.class.getName() + ".loginWithConfig";

    @Help("accounts data(loginId:password:name:roles,...)")
    @Separator(value = ';')
    List<String> accounts = Collections.unmodifiableList(Arrays.asList("admin:Adm1n:Administrator:Administrator"));

    @Help("file extension of text type contents")
    @Separator('|')
    List<String> text_extensions = Collections.unmodifiableList(Arrays.asList(".txt", ".htm", ".html", ".js", ".json", ".css", ".csv", ".tsv", ".xml", ".ini",
            ".yml", ".properties", ".php", ".java", ".jsp", ".xhtml"));

    @Help("http port(disabled if empty)")
    Optional<Integer> http_port = Optional.of(80);

    @Help("https port(disabled if empty, standard value is 443)")
    Optional<Integer> https_port = Optional.empty();

    @Help("https private key(etc. host.key)")
    Optional<String> https_key_file = Optional.empty();

    @Help("https cert(etc. host.crt)")
    List<String> https_cert_files = Collections.emptyList();

    @Help("context path")
    String context_path = "/";

    @Help("h2 web console port(disabled if empty)")
    Optional<Integer> h2_port = Optional.empty();

    @Help("h2 web console access allowed remote client")
    Boolean h2_allow_remote = false;

    @Help("h2 web console using https")
    Boolean h2_ssl = false;

    @Help("cluster node name suffix(for session cookie)")
    String cluster_suffix = "";

    @Help("controller packages")
    List<String> controller_packages = Collections.unmodifiableList(Arrays.asList("app.controller", "app.job"));

    @Help("job packages")
    List<String> job_packages = Collections.unmodifiableList(Arrays.asList("app.job"));

    @Help("model packages")
    List<String> model_packages = Collections.unmodifiableList(Arrays.asList("app.model"));

    public interface Item {
        String title = "タイトル";
        String login = "ログイン";
        String logout = "ログアウト";
        String error = "何か間違っていませんか？";
        String loginId = "ログインID";
        String password = "パスワード";
        String update = "変更";
        String insert = "追加";
        String delete = "削除";
        String before = "変更前";
        String after = "変更後";
        String back = "戻る";
        String quit = "終了";
        String diff = "差分表示";
        String index = "行";
        String compact = "省略表示";
        String full = "全行表示";
        String copyright = "©2016, All Rights Reserved.";
        String reader = "&#xFE19;";
        String run = "実行";
        String clear = "クリア";
    }

    public interface Alert {
        String forbidden = "アクセス権限がありません。権限のあるアカウントでログインしてください。";
        String login_failed = "ログインIDまたはパスワードが違います。";
    }

    public interface Prompt {
        String login = "ユーザ ID・ パスワードを入力して、 ログインボタンを押して下さい。";
    }
}