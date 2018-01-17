package framework;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import app.config.Sys;
import app.controller.Main;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Response.Status;
import framework.Tuple.Tuple3;
import framework.annotation.Config;
import framework.annotation.Content;
import framework.annotation.Job;
import framework.annotation.Letters;
import framework.annotation.Only;
import framework.annotation.Route;
import framework.annotation.Valid;
import framework.annotation.Validator;

/**
 * application scoped object
 */
@SuppressWarnings("serial")
public abstract class Application implements Attributes<Object> {

    /**
     * Singleton
     */
    static Application CURRENT;

    /**
     * Shutdown actions
     */
    protected List<Runnable> shutdowns = Tool
        .list(Log::shutdown, Job.Scheduler::shutdown, Try.r(Db::shutdown, e -> Log.warning("Db shutdown error")), () -> Tool.stream(DriverManager.getDrivers())
            .forEach(Try.c(DriverManager::deregisterDriver)));

    /**
     * routing table{{request method, pattern, bind map}: {class: method}}
     */
    static Map<Tuple3<Set<Route.Method>, Pattern, Map<String, String>>, Tuple<Class<?>, Method>> routing;

    /**
     * @return singleton
     */
    public static Optional<Application> current() {
        return Tool.of(CURRENT);
    }

    /**
     * @return context path
     */
    public abstract String getContextPath();

    @Override
    public String toString() {
        return "real path: " + Tool.val(Tool.trim(null, Tool.toURL("framework")
            .get()
            .toString(), "/"), s -> s.substring(0, s.length() - "framework".length())) + ", context path: " + getContextPath();
    }

    /**
     * @return Routes(method, path, action)
     */
    public static Stream<String[]> routes() {
        return routing.entrySet()
            .stream()
            .map(route -> Tool.array(route.getKey().l.isEmpty() ? "*"
                    : route.getKey().l.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", ")), route.getKey().r.l
                            .pattern(), route.getValue().l.getName() + "." + route.getValue().r.getName(), route.getKey().r.r.toString()))
            .sorted(Comparator.<String[], String>comparing(a -> a[1])
                .thenComparing(a -> a[0]));
    }

    /**
     * setup
     *
     * @param factory response factory
     */
    @SuppressFBWarnings({ "LI_LAZY_INIT_STATIC" })
    void setup(Supplier<Response> factory) {

        /* check to enabled of method parameters name */
        try {
            if (!"factory".equals(Application.class.getDeclaredMethod("setup", Supplier.class)
                .getParameters()[0].getName())) {
                throw new RuntimeException("must to enable compile option `-parameters`");
            }
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }

        /* load config */
        List<Class<?>> configClasses;
        try (Stream<Class<?>> cs = Tool.getClasses(Sys.class.getPackage()
            .getName())
            .filter(c -> Tool.fullName(c)
                .indexOf('.') < 0)) {
            configClasses = cs.peek(Config.Injector::inject)
                .peek(c -> Formatter.elClassMap.put(c.getSimpleName(), c))
                .collect(Collectors.toList());
        }

        /* load system properties */
        Config.Injector.loadSystemProperties();

        /* setup log */
        Log.startup();
        Log.info(Application.current()
            .get()::toString);

        /* setup for response creator */
        if (Response.factory == null) {
            Response.factory = factory;
        }

        /* setup routing */
        if (routing == null) {
            routing = new HashMap<>();
            try (Stream<Class<?>> cs = Tool.getClasses(Main.class.getPackage()
                .getName())) {
                cs.flatMap(c -> Stream.of(c.getDeclaredMethods())
                    .map(m -> Tuple.of(m, m.getAnnotation(Route.class)))
                    .filter(pair -> pair.r != null)
                    .map(pair -> Tuple.of(c, pair.l, pair.r)))
                    .collect(() -> routing, (map, trio) -> {
                        Class<?> clazz = trio.l;
                        Method method = trio.r.l;
                        String path = Tool.path("/", Tool.of(clazz.getAnnotation(Route.class))
                            .map(Route::value)
                            .orElse(""), trio.r.r.value())
                            .apply("/");
                        Map<String, String> renameMap = new HashMap<>();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        PrintWriter writer = new PrintWriter(out);
                        Tool.printReplace(writer, path, (w, to, prefix) -> {
                            String from;
                            if (!to.chars()
                                .allMatch(i -> (Letters.ALPHABETS + Letters.DIGITS).indexOf(i) >= 0)) {
                                from = String.format("HasH%08x", to.hashCode());
                                renameMap.put(from, to);
                            } else {
                                from = to;
                            }
                            writer.print("(?<" + from + ">");
                        }, "(?<", ">");
                        writer.flush();
                        path = out.toString();
                        method.setAccessible(true);
                        map.compute(Tuple.of(Tool.set(trio.r.r.method()), Pattern.compile(out.toString()), renameMap), (k, v) -> {
                            if (v != null) {
                                Log.warning("duplicated route: " + k + " [disabled] " + v.r + " [enabled] " + method);
                            }
                            return Tuple.of(clazz, method);
                        });
                    }, Map::putAll);
            }
            Log.info(() -> Tool.print(writer -> {
                writer.println("---- routing ----");
                routes().forEach(a -> writer.println(a[0] + " " + a[1] + " -> " + a[2] + " " + Tool.trim("{", a[3], "}")));
            }));
        }

        /* start h2 tcp server */
        Sys.h2_tcp_port.ifPresent(port -> {
            try {
                List<String> parameters = Tool.list("-tcpPort", String.valueOf(port));
                if (Sys.h2_tcp_allow_remote) {
                    parameters.add("-tcpAllowOthers");
                }
                if (Sys.h2_tcp_ssl) {
                    parameters.add("-tcpSSL");
                }
                Object tcp = Reflector.invoke("org.h2.tools.Server.createTcpServer", Tool
                    .array(String[].class), new Object[] { parameters.toArray(new String[parameters.size()]) });
                Reflector.invoke(tcp, "start", Tool.array());
                shutdowns.add(Try.r(() -> Reflector.invoke(tcp, "stop", Tool.array()), e -> Log.warning(e, () -> "h2 tcp server stop error")));
                Log.info("h2 tcp server started on port " + port);
            } catch (Exception e) {
                Log.warning(e, () -> "h2 tcp server error");
            }
        });

        /* start H2 web interface */
        Sys.h2_web_port.ifPresent(port -> {
            try {
                File config = new File(Tool.suffix(System.getProperty("java.io.tmpdir"), File.separator) + ".h2.server.properties");
                List<String> lines = new ArrayList<>();
                lines.add("webAllowOthers=" + Sys.h2_web_allow_remote);
                lines.add("webPort=" + port);
                lines.add("webSSL=" + Sys.h2_web_ssl);
                AtomicInteger index = new AtomicInteger(-1);
                Tool.val(Config.Injector.getSource(Sys.class, Session.currentLocale()), properties -> properties.stringPropertyNames()
                    .stream()
                    .sorted(String::compareTo)
                    .map(p -> Tuple.of(p, properties.getProperty(p)))
                    .filter(t -> t.l.startsWith("Sys.Db") && t.r.startsWith("jdbc:"))
                    .<String>map(t -> index.incrementAndGet() + "=" + t.l + "|" + Db.Type.fromUrl(t.r).driver + "|" + t.r.replace(":", "\\:")
                        .replace("=", "\\=")))
                    .forEach(lines::add);
                Files.write(config.toPath(), lines, StandardCharsets.UTF_8);
                config.deleteOnExit();
                Object db = Reflector
                    .invoke("org.h2.tools.Server.createWebServer", Tool.array(String[].class), new Object[] { Tool.array("-properties", config.getParent()) });
                Reflector.invoke(db, "start", Tool.array());
                shutdowns.add(Try.r(() -> Reflector.invoke(db, "stop", Tool.array()), e -> Log.warning(e, () -> "h2 web interface stop error")));
                Log.info("h2 web interface started on port " + port);
            } catch (Exception e) {
                Log.warning(e, () -> "h2 web interface error");
            }
        });

        /* database setup */
        Db.setup(Sys.Db.setup);

        /* load database config */
        Config.Injector.loadDb();

        Log.info(() -> "---- setting ----" + Letters.CRLF + configClasses.stream()
            .map(c -> String.join(Letters.CRLF, Config.Injector.dumpConfig(c, true)))
            .collect(Collectors.joining(Letters.CRLF)));

        Log.info(() -> "---- message ----" + Letters.CRLF + String.join(Letters.CRLF, Config.Injector.dumpMessage()));

        /* job scheduler setup */
        List<Class<?>> cs = new ArrayList<>();
        Sys.job_packages.stream()
            .forEach(p -> {
                try (Stream<Class<?>> classes = Tool.getClasses(p)) {
                    classes.forEach(cs::add);
                }
            });
        Job.Scheduler.setup(cs.toArray(new Class<?>[cs.size()]));
        Job.Scheduler.trigger(Job.OnApplicationStart);
    }

    /**
     * shutdown
     */
    void shutdown() {
        Job.Scheduler.trigger(Job.OnApplicationEnd);
        Collections.reverse(shutdowns);
        shutdowns.forEach(action -> action.run());
    }

    /**
     * request handle
     *
     * @param request request
     * @param session session
     */
    void handle(Request request, Session session) {
        Log.info(request::toString);

        final String path = request.getPath();

        /* no slash root access */
        if (path == null) {
            Response.redirect(getContextPath(), Status.Moved_Permamently)
                .flush();
            return;
        }

        Job.Scheduler.trigger(Job.OnRequest);

        /* action */
        final Optional<String> mime = Tool.string(Tool.getExtension(path))
            .map(Tool::getContentType);
        Map<String, List<String>> parameters = new HashMap<>(request.getParameters());
        final Tuple<Class<?>, Method> pair = routing.entrySet()
            .stream()
            .filter(e -> e.getKey().l.isEmpty() || e.getKey().l.contains(request.getMethod()))
            .map(e -> Tuple.of(e.getKey().r.l.matcher(path), e.getKey().r.r, e.getValue()))
            .filter(p -> p.l.matches())
            .sorted(Comparator.comparing(c -> c.getValue()
                .getValue()
                .getValue()
                .getAnnotation(Route.class)
                .priority(), Comparator.reverseOrder()))
            .findFirst()
            .map(p -> {
                Reflector.<Map<String, Integer>>invoke(p.l.pattern(), "namedGroups", Tool.array())
                    .forEach((k, v) -> Tool.setValue(parameters, p.r.l.getOrDefault(k, k), p.l.group(v)));
                return p.r.r;
            })
            .orElse(null);
        if (pair != null && Tool.of(pair.r.getAnnotation(Content.class))
            .map(Content::value)
            .map(i -> Tool.list(i)
                .contains(mime.orElse("")))
            .orElse(true)) {
            do {
                Method method = pair.r;
                Only only = Tool.or(method.getAnnotation(Only.class), () -> method.getDeclaringClass()
                    .getAnnotation(Only.class))
                    .orElse(null);

                /* go login page if not logged in */
                if (only != null && Sys.redirect_if_not_login.filter(i -> !i.equals(path))
                    .isPresent() && !session.isLoggedIn()) {
                    String host = Tool.getFirst(request.getHeaders(), "Host")
                        .orElse("localhost");
                    String referer = Tool.getFirst(request.getHeaders(), "Referer")
                        .orElse("");
                    if (referer.contains(host) && !session.containsKey("alert")) {
                        session.put("alert", Sys.Alert.timeout);
                    }
                    if(Tool.getFirst(request.getHeaders(), "X-requested-with").filter(i -> i.equals("XMLHttpRequest")).isPresent()) {
                        Response.error(Status.No_Content).flush();
                        return;
                    }
                    Response.redirect(Tool.path(getContextPath(), Sys.redirect_if_not_login.get())
                        .apply("/"))
                        .flush();
                    return;
                }

                /* forbidden check */
                boolean forbidden = only != null && !session.isLoggedIn();
                if (!forbidden && only != null && only.value().length > 0) {
                    forbidden = !session.getAccount()
                        .hasAnyRole(only.value());
                }
                if (forbidden) {
                    session.setAttr("alert", Sys.Alert.forbidden);
                    Response.template("error.html")
                        .flush();
                    return;
                }

                try (Lazy<Db> db = new Lazy<>(Db::connect)) {
                    try {
                        Log.config("[invoke method] " + method.getDeclaringClass()
                            .getName() + "." + method.getName());
                        Binder binder = new Binder(parameters).files(request.getFiles());
                        Object[] args = Stream.of(method.getParameters())
                            .map(p -> {
                                Class<?> type = p.getType();
                                if (Request.class.isAssignableFrom(type)) {
                                    return request;
                                }
                                if (Session.class.isAssignableFrom(type)) {
                                    return session;
                                }
                                if (Application.class.isAssignableFrom(type)) {
                                    return this;
                                }
                                if (Db.class.isAssignableFrom(type)) {
                                    return db.get();
                                }
                                String name = p.getName();
                                return binder.validator(value -> Stream.of(p.getAnnotations())
                                    .forEach(a -> {
                                        Validator.Constructor.instance(a)
                                            .validate(Valid.All.class, name, value, binder);
                                    }))
                                    .bind(name, type, Reflector.getGenericParameters(p));
                            })
                            .toArray();
                        Object response;
                        if (binder.errors.isEmpty()) {
                            response = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : Reflector.instance(pair.l), args);
                        } else {
                            response = Response.of(Tool.array(Sys.Alert.inputError, binder.errors)).contentType(Content.JSON);
                        }
                        Consumer<Response> setContentType = r -> {
                            Content content = method.getAnnotation(Content.class);
                            String[] accept = request.getHeaders()
                                .getOrDefault("accept", Collections.emptyList())
                                .stream()
                                .flatMap(i -> Stream.of(i.split("\\s*,\\s*"))
                                    .map(j -> j.replaceAll(";.*$", "")
                                        .trim()
                                        .toLowerCase(Locale.ENGLISH)))
                                .toArray(String[]::new);
                            Tool.ifPresentOr(Tool.or(mime, () -> Stream.of(accept)
                                .filter(i -> content == null || Stream.of(content.value())
                                    .anyMatch(i::equals))
                                .findFirst()), m -> r.contentType(m, Tool.isTextContent(path) ? StandardCharsets.UTF_8 : null), () -> {
                                    throw new RuntimeException("not accept mime type: " + Arrays.toString(accept));
                                });
                        };
                        if (response instanceof Response) {
                            Tool.peek((Response) response, r -> {
                                if (r.headers == null || !r.headers.containsKey("Content-Type")) {
                                    setContentType.accept(r);
                                }
                            })
                                .flush();
                        } else {
                            Tool.peek(Response.of(response), setContentType::accept)
                                .flush();
                        }
                        return;
                    } catch (InvocationTargetException e) {
                        db.ifGot(Db::rollback);
                        Throwable t = e.getCause();
                        if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        }
                        throw new RuntimeException(t);
                    } catch (IllegalAccessException e) {
                        db.ifGot(Db::rollback);
                        throw new RuntimeException(e);
                    } catch (RuntimeException e) {
                        db.ifGot(Db::rollback);
                        throw e;
                    }
                }
            } while (false);
        }

        /* static file */
        Response.file(path)
            .flush();
    }
}
