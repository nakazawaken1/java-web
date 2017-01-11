package framework;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.controller.Main;
import framework.Db.Setup;
import framework.annotation.Http;
import framework.annotation.Only;
import framework.annotation.Query;

/**
 * Servlet implementation class Main
 */
@WebServlet("/")
public class Server implements Servlet {

    /**
     * application scope object
     */
    transient static Application application;

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Server.class.getCanonicalName());

    /**
     * routing table
     */
    transient private static final Map<String, Method> table;

    /**
     * controller
     */
    transient static final Class<?> controller = Main.class;

    static {
        table = Stream.of(controller.getDeclaredMethods()).filter(i -> i.getAnnotation(Http.class) != null).collect(Collectors.toMap(Method::getName, m -> {
            m.setAccessible(true);
            return m;
        }));
    }
    
    /**
     * for initialize
     */
    private static final AtomicBoolean initialized = new AtomicBoolean();
    
    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            if (!"config".equals(Server.class.getMethod("init", ServletConfig.class).getParameters()[0].getName())) {
                throw new ServletException("must to enable compile option `-parameters`");
            }
        } catch (NoSuchMethodException | SecurityException e) {
        }
        Config.startupLog();
        if (application == null) {
            application = new Application(config.getServletContext());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#destroy()
     */
    @Override
    public void destroy() {
        try {
            Db.cleanup();
            Tool.stream(DriverManager.getDrivers()).forEach(Try.c(DriverManager::deregisterDriver));
        } catch (Exception e) {
            logger.log(Level.WARNING, "destroy error", e);
        }
        Config.shutdownLog();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#getServletConfig()
     */
    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#getServletInfo()
     */
    @Override
    public String getServletInfo() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        Tool.stream(req.getParameterNames()).forEach(logger::info);
        if(initialized.compareAndSet(false, true)) {
            Db.setup(Config.db_setup.enumeration(Setup.class));
        }
        Request.request.set((HttpServletRequest) req);
        Request.response.set((HttpServletResponse) res);
        Request request = new Request();
        if (request.path == null) { /* no slash folder access */
            new Response(r -> {
                r.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                r.setHeader("Location", application.contextPath());
            }).flush();
            return;
        }
        Session session = new Session();
        try {
            if (Config.toURL(Config.app_view_folder.text(), request.path).isPresent()) { /* exists */
                Response.file(request.path).flush();
                return;
            }
            Method method = table.get(request.path);
            if (method != null) {
                Only only = method.getAnnotation(Only.class);
                boolean forbidden = only != null && !session.account().isPresent();
                if (!forbidden && only != null && only.value().length > 0) {
                    forbidden = !session.account().get().hasAnyRole(only.value());
                }
                if (forbidden) {
                    session.setAttr("flush", "アクセス権限がありません。権限のあるアカウントでログインしてください");
                    Response.redirect(application.contextPath()).flush();
                    return;
                }
                try {
                    ((Response) method.invoke(controller.newInstance(), Stream.of(method.getParameters()).map(p -> {
                        Class<?> type = p.getType();
                        if (Request.class.isAssignableFrom(type)) {
                            return request;
                        }
                        if (Session.class.isAssignableFrom(type)) {
                            return session;
                        }
                        if (Application.class.isAssignableFrom(type)) {
                            return application;
                        }
                        if (p.getAnnotation(Query.class) != null) {
                            String value = request.raw.getParameter(p.getName());
                            return convert(type, p, value);
                        }
                        return null;
                    }).toArray())).flush();
                    return;
                } catch (InvocationTargetException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }
                    throw new RuntimeException(t);
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
                    throw new RuntimeException(e);
                }
            }
            throw new FileNotFoundException(request.path);
        } finally {
            Request.request.remove();
            Request.response.remove();
        }
    }

    /**
     * @param type value type
     * @param p parameter
     * @param value string value
     * @return value
     */
    private Object convert(Type type, Parameter p, String value) {
        if (type == int.class || type == Integer.class) {
            return Tool.integer(value).orElse(0);
        }
        if (type == Optional.class) {
            Type valueType = ((ParameterizedType) p.getParameterizedType()).getActualTypeArguments()[0];
            return Optional.ofNullable(valueType == String.class ? value : convert(valueType, null, value))
                    .filter(i -> !(i instanceof String) || !((String) i).isEmpty());
        }
        return value;
    }
}
