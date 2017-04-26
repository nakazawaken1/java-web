package framework;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import app.config.Sys;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Response.Status;
import framework.annotation.Config.Injector;
import framework.annotation.Content;
import framework.annotation.Job;
import framework.annotation.Letters;
import framework.annotation.Only;
import framework.annotation.Route;

/**
 * application scoped object
 */
public abstract class Application implements Attributes<Object> {

    /**
     * singleton
     */
    static Application CURRENT;

    /**
     * routing table{path: {class: method}}
     */
    static SortedMap<String, Tuple<Class<?>, Method>> table;

    /**
     * getters
     */
    Getters getters = new Getters(Application.class);

    /**
     * @return singleton
     */
    static Optional<Application> current() {
        return Optional.ofNullable(CURRENT);
    }

    /**
     * @return request id
     */
    public int getId() {
        return hashCode();
    }

    /**
     * @return context path
     */
    abstract String getContextPath();

    @Override
    public String toString() {
        return "real path: " + Tool.val(Tool.trim(null, Tool.toURL("framework").get().toString(), "/"), s -> s.substring(0, s.length() - "framework".length()))
                + ", context path: " + getContextPath();
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
            if (!"factory".equals(Application.class.getDeclaredMethod("setup", Supplier.class).getParameters()[0].getName())) {
                throw new RuntimeException("must to enable compile option `-parameters`");
            }
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }

        Injector.inject(Sys.class);
        Log.startup();
        Log.info(() -> "---- setting ----" + Letters.CRLF + String.join(Letters.CRLF, Injector.dump(Sys.class, true)));

        Log.info(Application.current().get()::toString);

        /* setup for response creator */
        if (Response.factory == null) {
            Response.factory = factory;
        }

        /* setup routing */
        if (table == null) {
            table = new TreeMap<>();
            Sys.controller_packages.stream().forEach(p -> {
                try (Stream<Class<?>> cs = Tool.getClasses(p)) {
                    cs.flatMap(c -> Stream.of(c.getDeclaredMethods()).map(m -> Tuple.of(m, m.getAnnotation(Route.class))).filter(pair -> pair.r != null)
                            .map(pair -> Tuple.of(c, pair.l, pair.r))).collect(() -> table, (map, trio) -> {
                                Class<?> c = trio.l;
                                Method m = trio.r.l;
                                String left = Optional.ofNullable(c.getAnnotation(Route.class))
                                        .map(a -> Tool.string(a.path()).orElse(c.getSimpleName().toLowerCase() + '/')).orElse("");
                                String right = Tool.string(trio.r.r.path()).orElse(m.getName());
                                m.setAccessible(true);
                                map.put(left + right, Tuple.of(c, m));
                            }, Map::putAll);
                }
            });
            Log.info(() -> Tool.print(writer -> {
                writer.println("---- routing ----");
                table.forEach((path, pair) -> writer.println(path + " -> " + pair.l.getName() + "." + pair.r.getName()));
            }));
        }

        /* database setup */
        Db.setup(Sys.Db.setup);

        /* job scheduler setup */
        List<Class<?>> cs = new ArrayList<>();
        Sys.job_packages.stream().forEach(p -> {
            try (Stream<Class<?>> classes = Tool.getClasses(p)) {
                classes.forEach(cs::add);
            }
        });
        Job.Scheduler.setup(cs.toArray(new Class<?>[cs.size()]));
    }

    /**
     * shutdown
     */
    void shutdown() {
        try {
            Job.Scheduler.shutdown();
            Db.shutdown();
            Tool.stream(DriverManager.getDrivers()).forEach(Try.c(DriverManager::deregisterDriver));
        } catch (Exception e) {
            Log.warning(e, () -> "destroy error");
        }
        Log.shutdown();
    }

    /**
     * request handle
     *
     * @param request request
     * @param session session
     */
    void handle(Request request, Lazy<Session> session) {
        Log.info(request::toString);

        /* no slash root access */
        final String path = request.getPath();
        if (path == null) {
            Response.redirect(getContextPath(), Status.Moved_Permamently).flush();
            return;
        }

        /* action */
        Optional<String> mime;
        final String action;
        int index = path.lastIndexOf('.');
        if (index >= 0 && index + 5 >= path.length()) {
            mime = Optional.ofNullable(Tool.getContentType(path));
            action = path.substring(0, index);
        } else {
            mime = Optional.empty();
            action = path;
        }
        final Tuple<Class<?>, Method> pair = table.get(action);
        if (pair != null) {
            do {
                Method method = pair.r;
                Route http = method.getAnnotation(Route.class);
                if (http == null || http.value().length > 0 && !Arrays.asList(http.value()).contains(request.getMethod())) {
                    break;
                }
                Only only = method.getAnnotation(Only.class);
                boolean forbidden = only != null && !session.get().isLoggedIn();
                if (!forbidden && only != null && only.value().length > 0) {
                    forbidden = !session.get().getAccount().hasAnyRole(only.value());
                }
                if (forbidden) {
                    session.get().setAttr("alert", Sys.Alert.forbidden);
                    Response.redirect(getContextPath()).flush();
                    return;
                }
                try (Lazy<Db> db = new Lazy<>(Db::connect)) {
                    try {
                        Object response = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : Reflector.instance(pair.l),
                                Stream.of(method.getParameters()).map(p -> {
                                    Class<?> type = p.getType();
                                    if (Request.class.isAssignableFrom(type)) {
                                        return request;
                                    }
                                    if (Session.class.isAssignableFrom(type)) {
                                        return session.get();
                                    }
                                    if (Application.class.isAssignableFrom(type)) {
                                        return this;
                                    }
                                    if (Db.class.isAssignableFrom(type)) {
                                        return db.get();
                                    }
                                    Type types = p.getParameterizedType();
                                    return new Binder(request.getParameters()).bind(p.getName(), type,
                                            types instanceof ParameterizedType ? ((ParameterizedType) types).getActualTypeArguments() : Tool.array());
                                }).toArray());
                        if (response instanceof Response) {
                            Tool.peek((Response) response, r -> r.charset.orElseGet(() -> Tool.isTextContent(path) ? StandardCharsets.UTF_8 : null)).flush();
                        } else {
                            Content content = method.getAnnotation(Content.class);
                            String[] accept = request.getHeaders().get("accept").stream()
                                    .flatMap(i -> Stream.of(i.split("\\s*,\\s*")).map(j -> j.replaceAll(";.*$", "").trim().toLowerCase()))
                                    .toArray(String[]::new);
                            if (!mime.isPresent()) {
                                mime = content == null ? Stream.of(accept).findFirst()
                                        : Stream.of(accept).filter(i -> Stream.of(content.value()).anyMatch(i::equals)).findFirst();
                            }
                            Tool.ifPresentOr(mime, m -> Response.of(response).contentType(m, Tool.isTextContent(path) ? StandardCharsets.UTF_8 : null).flush(),
                                    () -> {
                                        throw new RuntimeException("not accept mime type: " + Arrays.toString(accept));
                                    });
                        }
                        return;
                    } catch (InvocationTargetException e) {
                        Throwable t = e.getCause();
                        if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        }
                        throw new RuntimeException(t);
                    } catch (IllegalAccessException | IllegalArgumentException e) {
                        throw new RuntimeException(e);
                    } catch (RuntimeException e) {
                        throw e;
                    }
                }
            } while (false);
        }

        /* static file */
        Response.file(path).flush();
    }
}
