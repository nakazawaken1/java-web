package framework;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import app.config.Sys;
import app.controller.Main;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Response.Status;
import framework.annotation.Config;
import framework.annotation.Content;
import framework.annotation.Job;
import framework.annotation.Letters;
import framework.annotation.Only;
import framework.annotation.Route;
import framework.annotation.Valid;

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
    static Map<Tuple<Set<Route.Method>, String>, Tuple<Class<?>, Method>> routing;

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

        /* load config */
        List<Class<?>> configClasses;
        try (Stream<Class<?>> cs = Tool.getClasses(Sys.class.getPackage().getName()).filter(c -> Tool.fullName(c).indexOf('.') < 0)) {
            configClasses = cs.peek(Config.Injector::inject).peek(c -> Formatter.elClassMap.put(c.getSimpleName(), c)).collect(Collectors.toList());
        }
        Log.startup();
        Log.info(() -> "---- setting ----" + Letters.CRLF
                + configClasses.stream().map(c -> String.join(Letters.CRLF, Config.Injector.dumpConfig(c, true))).collect(Collectors.joining(Letters.CRLF)));
        Log.info(() -> "---- message ----" + Letters.CRLF + String.join(Letters.CRLF, Config.Injector.dumpMessage()));

        Log.info(Application.current().get()::toString);

        /* setup for response creator */
        if (Response.factory == null) {
            Response.factory = factory;
        }

        /* setup routing */
        if (routing == null) {
            routing = new HashMap<>();
            try (Stream<Class<?>> cs = Tool.getClasses(Main.class.getPackage().getName())) {
                cs.flatMap(c -> Stream.of(c.getDeclaredMethods()).map(m -> Tuple.of(m, m.getAnnotation(Route.class))).filter(pair -> pair.r != null)
                        .map(pair -> Tuple.of(c, pair.l, pair.r))).collect(() -> routing, (map, trio) -> {
                            Class<?> c = trio.l;
                            Method m = trio.r.l;
                            String left = Tool.of(c.getAnnotation(Route.class)).map(Route::value).orElse("");
                            String right = trio.r.r.value();
                            m.setAccessible(true);
                            map.compute(Tuple.of(Tool.set(trio.r.r.method()), Tool.path("/", left, right).apply("/")), (k, v) -> {
                                if (v != null) {
                                    Log.warning("duplicated route: " + k + " [disabled] " + v.r + " [enabled] " + m);
                                }
                                return Tuple.of(c, m);
                            });
                        }, Map::putAll);
            }
            Log.info(() -> Tool.print(writer -> {
                writer.println("---- routing ----");
                routing.forEach((key, value) -> writer
                        .println((key.l.isEmpty() ? "*" : key.l.toString()) + " " + key.r + " -> " + value.l.getName() + "." + value.r.getName()));
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
    void handle(Request request, Session session) {
        Log.info(request::toString);

        final String path = request.getPath();

        /* no slash root access */
        if (path == null) {
            Response.redirect(getContextPath(), Status.Moved_Permamently).flush();
            return;
        }

        /* action */
        final Optional<String> mime = Tool.string(Tool.getExtension(path)).map(Tool::getContentType);
        Map<String, List<String>> parameters = new HashMap<>(request.getParameters());
        final Tuple<Class<?>, Method> pair = routing.entrySet().stream().filter(e -> e.getKey().l.isEmpty() || e.getKey().l.contains(request.getMethod()))
                .map(e -> Tuple.of(Pattern.compile(e.getKey().r).matcher(path), e.getValue())).filter(p -> p.l.matches()).findAny().map(p -> {
                    Reflector.<Map<String, Integer>>invoke(p.l.pattern(), "namedGroups", Tool.array())
                            .forEach((k, v) -> Tool.addValue(parameters, k, p.l.group(v)));
                    return p.r;
                }).orElse(null);
        if (pair != null && Tool.of(pair.r.getAnnotation(Content.class)).map(Content::value).map(i -> Tool.list(i).contains(mime.orElse(""))).orElse(true)) {
            do {
                Method method = pair.r;
                Only only = Tool.or(method.getAnnotation(Only.class), () -> method.getDeclaringClass().getAnnotation(Only.class)).orElse(null);

                /* go login page if not logged in */
                if (only != null && Sys.redirect_if_not_login.filter(i -> !i.equals(path)).isPresent() && !session.isLoggedIn()) {
                    Response.redirect(Tool.path(getContextPath(), Sys.redirect_if_not_login.get()).apply("/")).flush();
                    return;
                }

                /* forbidden check */
                boolean forbidden = only != null && !session.isLoggedIn();
                if (!forbidden && only != null && only.value().length > 0) {
                    forbidden = !session.getAccount().hasAnyRole(only.value());
                }
                if (forbidden) {
                    session.setAttr("alert", Sys.Alert.forbidden);
                    Response.template("error.html").flush();
                    return;
                }

                try (Lazy<Db> db = new Lazy<>(Db::connect)) {
                    Log.config("[invoke method] " + method.getDeclaringClass().getName() + "." + method.getName());
                    Binder binder = new Binder(parameters);
                    Object[] args = Stream.of(method.getParameters()).map(p -> {
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
                        Type[] types = Tool.of(p.getParameterizedType()).filter(i -> i instanceof ParameterizedType).map(i -> (ParameterizedType) i)
                                .map(ParameterizedType::getActualTypeArguments).orElseGet(Tool::array);
                        return binder.validator(value -> Stream.of(p.getAnnotations()).forEach(a -> {
                            Class<? extends Annotation> c = a.annotationType();
                            Stream.of(c.getDeclaredClasses()).filter(i -> AbstractValidator.class.isAssignableFrom(i)).findFirst()
                                    .flatMap(i -> Reflector.constructor(i, c)).map(Try.f(n -> (AbstractValidator<?>) n.newInstance(a)))
                                    .ifPresent(i -> i.validate(Valid.All.class, name, value, binder));
                        })).bind(name, type, types);
                    }).toArray();
                    Object response;
                    if (binder.errors.isEmpty()) {
                        response = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : Reflector.instance(pair.l), args);
                    } else {
                        response = Response.of(Tool.array(Sys.Alert.inputError, binder.errors));
                    }
                    Consumer<Response> setContentType = r -> {
                        Content content = method.getAnnotation(Content.class);
                        String[] accept = request.getHeaders().get("accept").stream()
                                .flatMap(i -> Stream.of(i.split("\\s*,\\s*")).map(j -> j.replaceAll(";.*$", "").trim().toLowerCase(Locale.ENGLISH)))
                                .toArray(String[]::new);
                        Tool.ifPresentOr(
                                Tool.or(mime,
                                        () -> Stream.of(accept).filter(i -> content == null || Stream.of(content.value()).anyMatch(i::equals)).findFirst()),
                                m -> r.contentType(m, Tool.isTextContent(path) ? StandardCharsets.UTF_8 : null), () -> {
                                    throw new RuntimeException("not accept mime type: " + Arrays.toString(accept));
                                });
                    };
                    if (response instanceof Response) {
                        Tool.peek((Response) response, r -> {
                            if (r.headers == null || !r.headers.containsKey("Content-Type")) {
                                setContentType.accept(r);
                            }
                        }).flush();
                    } else {
                        Tool.peek(Response.of(response), r -> setContentType.accept(r)).flush();
                    }
                    return;
                } catch (InvocationTargetException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }
                    throw new RuntimeException(t);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (RuntimeException e) {
                    throw e;
                }
            } while (false);
        }

        /* static file */
        Response.file(path).flush();
    }
}
