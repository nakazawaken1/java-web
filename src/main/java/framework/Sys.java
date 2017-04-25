package framework;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.regex.Pattern;

import framework.Db.Setup;
import framework.annotation.Config;
import framework.annotation.Help;
import framework.annotation.Separator;

@SuppressWarnings("javadoc")
@Config({ "sys.config", "sys.message" })
public interface Sys {

    /**
     * backup
     */
    Properties properties = new Properties();

    public interface Log {
        @Help("log output folder")
        String folder = "/temp/";

        @Help("log filename pattern(DateTimeFormatter format, 'll' replace to level)")
        DateTimeFormatter file_pattern = DateTimeFormatter.ofPattern("'ll_'yyyyMMdd'.log'");

        @Help("log line format")
        String format = "%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %3$s [%2$s] %5$s %6$s%n";

        @Help("output log level")
        Level level = Level.CONFIG;

        @Help("classes of suppress log")
        @Separator(',')
        List<String> exclude = Arrays.asList();
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
    int session_timeout_minutes = 30;

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
    int job_threads = 1;

    @Help("login method(must to be static method)")
    String login_method = "framework.Account.loginWithConfig";

    @Help("accounts data(loginId:password:name:roles,...)")
    @Separator(value = ';')
    List<String> accounts = Arrays.asList("admin:Adm1n:Administrator:Administrator");

    @Help("file extension of text type contents")
    @Separator('|')
    List<String> text_extensions = Arrays.asList(".txt", ".htm", ".html", ".js", ".json", ".css", ".csv", ".tsv", ".xml", ".ini", ".yml", ".properties", ".php",
            ".java", ".jsp", ".xhtml");

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
    boolean h2_allow_remote = false;

    @Help("h2 web console using https")
    boolean h2_ssl = false;

    @Help("cluster node name suffix(for session cookie)")
    String cluster_suffix = "";

    @Help("controller packages")
    List<String> controller_packages = Arrays.asList("app.controller", "app.job");

    @Help("job packages")
    List<String> job_packages = Arrays.asList("app.job");

    @Help("model packages")
    List<String> model_packages = Arrays.asList("app.model");

    @Help("eval log max output letters")
    int eval_log_max_letters = 100;

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
    }

    public interface Alert {
        String forbidden = "アクセス権限がありません。権限のあるアカウントでログインしてください。";
        String login_failed = "ログインIDまたはパスワードが違います。";
    }

    public interface Prompt {
        String login = "ユーザ ID・ パスワードを入力して、 ログインボタンを押して下さい。";
    }
}
