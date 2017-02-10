package framework;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Db.Setup;
import framework.Response.ResponseCreator;
import framework.annotation.Http;
import framework.annotation.Job;
import framework.annotation.Only;
import framework.annotation.Query;
import java.io.File;
import java.net.URL;

/**
 * Servlet implementation class
 */
@SuppressWarnings("restriction")
@WebServlet("/")
public class Server implements Servlet {

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Server.class.getCanonicalName());

    /**
     * routing table<path, <class, method>>
     */
    transient static Map<String, Pair<Class<?>, Method>> table;

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
     */
    @Override
    @SuppressFBWarnings({"LI_LAZY_INIT_UPDATE_STATIC", "LI_LAZY_INIT_STATIC"})
    public void init(ServletConfig config) throws ServletException {
        setup(new Lazy<>(() -> new Application.ForServlet(config.getServletContext())), Response.ForServlet::new);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#destroy()
     */
    @Override
    public void destroy() {
        shutdown();
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
        try (Defer<Request> request = new Defer<>(new Request.ForServlet((HttpServletRequest) req, (HttpServletResponse) res), r -> Request.current.remove());
                Defer<Lazy<Session>> session = new Defer<>(new Lazy<>(() -> {
                    Session s = new Session.ForServlet(((HttpServletRequest) req).getSession());
                    Session.current.set(s);
                    return s;
                }), s -> s.ifGot(i -> Session.current.remove()).close())) {
            Request.current.set(request.get());
            handle(request.get(), session.get());
        }
    }

    /**
     * setup
     *
     * @param applicationGetter applicationGetter
     * @param responseCreator responseCreator
     */
    @SuppressFBWarnings({"LI_LAZY_INIT_STATIC"})
    static void setup(Lazy<Application> applicationGetter, Supplier<ResponseCreator> responseCreator) {

        /* check to enabled of method parameters name */
        try {
            if (!"config".equals(Server.class.getMethod("init", ServletConfig.class).getParameters()[0].getName())) {
                throw new RuntimeException("must to enable compile option `-parameters`");
            }
        } catch (NoSuchMethodException | SecurityException e) {
        }

        /* log setup */
        Config.startupLog();

        /* create application scope object */
        if (Application.current.get() == null) {
            Application.current.set(applicationGetter.get());
            logger.info(Application.current.get().toString());
        }

        /* setup for response creator */
        if (Response.create == null) {
            Response.create = responseCreator;
        }

        /* setup routing */
        if (table == null) {
            try (Stream<Class<?>> classes = Tool.getClasses("app.controller")) {
                table = classes.flatMap(c -> Stream.of(c.getDeclaredMethods()).map(m -> Tool.pair(m, m.getAnnotation(Http.class)))
                        .filter(pair -> pair.b != null).map(pair -> Tool.trio(c, pair.a, pair.b))).collect(Collectors.toMap(trio -> {
                    Class<?> c = trio.a;
                    Method m = trio.b;
                    String left = Optional.ofNullable(c.getAnnotation(Http.class))
                            .map(a -> Tool.string(a.path()).orElse(c.getSimpleName().toLowerCase() + '/')).orElse("");
                    String right = Tool.string(trio.c.path()).orElse(m.getName());
                    return left + right;
                }, trio -> {
                    Method m = trio.b;
                    m.setAccessible(true);
                    return Tool.pair(trio.a, m);
                }));
            }
            logger.info(Tool.print(writer -> {
                writer.println("---- routing ----");
                table.forEach((path, pair) -> writer.println(path + " -> " + pair.a.getName() + "." + pair.b.getName()));
            }));
        }

        /* database setup */
        Db.setup(Config.db_setup.enumOf(Setup.class));

        /* job scheduler setup */
        try (Stream<Class<?>> classes = Tool.getClasses("app.controller")) {
            Job.Scheduler.setup(classes.toArray(Class<?>[]::new));
        }
    }

    /**
     * shutdown
     */
    static void shutdown() {
        try {
            Job.Scheduler.shutdown();
            Db.cleanup();
            Tool.stream(DriverManager.getDrivers()).forEach(Try.c(DriverManager::deregisterDriver));
        } catch (Exception e) {
            logger.log(Level.WARNING, "destroy error", e);
        }
        Config.shutdownLog();
    }

    /**
     * request handle
     *
     * @param request request
     * @param session session
     * @throws ServletException
     * @throws IOException
     */
    static void handle(Request request, Lazy<Session> session) throws ServletException, IOException {
        logger.info(request.toString());

        Application application = Application.current.get();

        /* no slash root access */
        if (request.getPath() == null) {
            Response.redirect(application.getContextPath(), 301).flush();
            return;
        }

        /* action */
        Pair<Class<?>, Method> pair = table.get(request.getPath());
        if (pair != null) {
            Method method = pair.b;
            Http http = method.getAnnotation(Http.class);
            if (http == null || (http.value().length > 0 && !Arrays.asList(http.value()).contains(request.getMethod()))) {
                Response.error(400).flush();
                return;
            }
            Only only = method.getAnnotation(Only.class);
            boolean forbidden = only != null && !session.get().isLoggedIn();
            if (!forbidden && only != null && only.value().length > 0) {
                forbidden = !session.get().getAccount().hasAnyRole(only.value());
            }
            if (forbidden) {
                session.get().setAttr("alert", Message.alert_forbidden);
                Response.redirect(application.getContextPath()).flush();
                return;
            }
            try (Lazy<Db> db = new Lazy<>(Db::connect)) {
                try {
                    ((Response) method.invoke(Modifier.isStatic(method.getModifiers()) ? null : pair.a.newInstance(),
                            Stream.of(method.getParameters()).map(p -> {
                                Class<?> type = p.getType();
                                if (Request.class.isAssignableFrom(type)) {
                                    return request;
                                }
                                if (Session.class.isAssignableFrom(type)) {
                                    return session.get();
                                }
                                if (Application.class.isAssignableFrom(type)) {
                                    return application;
                                }
                                if (Db.class.isAssignableFrom(type)) {
                                    return db.get();
                                }
                                if (p.getAnnotation(Query.class) != null) {
                                    return parseValue(type, p, request.getFirstParameter(p.getName()));
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
                } catch (RuntimeException e) {
                    throw e;
                }
            }
        }

        /* static file */
        Optional<URL> url = Config.toURL(Config.app_view_folder.text(), request.getPath());
        if (url.isPresent()) {
            if (!url.filter(Try.p(i -> {
                i.openStream().close();
                return true;
            }, e -> {
            })).isPresent()) {
                Response.redirect(Tool.trim(null, application.getContextPath(), "/") + Tool.suffix(request.getPath(), "/") + "index.html", 301).flush();
            } else {
                Response.file(request.getPath()).flush();
            }
            return;
        }

        throw new FileNotFoundException(request.getPath());
    }

    /**
     * @param type value type
     * @param p parameter
     * @param value string value
     * @return value
     */
    static Object parseValue(Type type, Parameter p, String value) {
        if (type == int.class || type == Integer.class) {
            return Tool.integer(value).orElse(0);
        }
        if (type == Optional.class) {
            Type valueType = ((ParameterizedType) p.getParameterizedType()).getActualTypeArguments()[0];
            return Optional.ofNullable(valueType == String.class ? value : parseValue(valueType, null, value))
                    .filter(i -> !(i instanceof String) || !((String) i).isEmpty());
        }
        return value;
    }

    /**
     * @param args context-path http-port https-port .key .crt...
     */
    @SuppressFBWarnings({"LI_LAZY_INIT_STATIC"})
    public static void main(String[] args) {

        String contextPath = Tool.suffix(args.length > 0 ? args[0] : "", "/");
        int httpPort = args.length > 1 ? Integer.parseInt(args[1]) : 80;
        int httpsPort = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        String keyPath = args.length > 3 ? args[3] : null;
        Stream<String> certPaths = args.length > 4 ? Stream.of(args).skip(4) : Stream.empty();

        setup(new Lazy<>(() -> new Application.ForServer(contextPath)), Response.ForServer::new);

        // start HTTPS server
        HttpHandler handler = exchange -> {
            try (Defer<Request> request = new Defer<>(new Request.ForServer(exchange), r -> Request.current.remove());
                    Defer<Lazy<Session>> session = new Defer<>(new Lazy<>(() -> {
                        Session s = new Session.ForServer(exchange);
                        Session.current.set(s);
                        return s;
                    }), s -> s.ifGot(i -> Session.current.remove()).close())) {
                Request.current.set(request.get());
                handle(request.get(), session.get());
            } catch (Exception e) {
                logger.log(Level.WARNING, "500", e);
            }
        };
        Executor executor = Executors.newWorkStealingPool();

        try {
            if (httpPort > 0) {
                HttpServer http = HttpServer.create(new InetSocketAddress(httpPort), 0);
                http.setExecutor(executor);
                http.createContext(contextPath, handler);
                http.start();
                logger.info("http server started on port " + httpPort);
            }

            if (httpsPort > 0) {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(createSSLContext(keyPath, certPaths)));
                https.setExecutor(executor);
                https.createContext(contextPath, handler);
                https.start();
                logger.info("https server started on port " + httpsPort);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "setup error", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(Server::shutdown));
    }

    /**
     * @param keyPath key file
     * @param certPaths cert files
     * @return SSLContext
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws KeyStoreException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    static SSLContext createSSLContext(String keyPath, Stream<String> certPaths) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException {

        // load private key(.key)
        PrivateKey key;
        try (Stream<String> lines = Files.lines(Paths.get(keyPath))) {
            String text = lines.filter(line -> !line.isEmpty() && !line.startsWith("--") && line.indexOf(':') < 0).collect(Collectors.joining());
            byte[] bytes = Base64.getMimeDecoder().decode(text);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            try {
                key = factory.generatePrivate(new PKCS8EncodedKeySpec(bytes)); // PKCS#8
            } catch (InvalidKeySpecException e) {
                DERReader reader = new DERReader(bytes);
                Pair<Integer, byte[]> pair = reader.read(); // sequence
                if ((pair.a & 0x1f) != 0x10) {
                    throw new InvalidKeySpecException("first part is not sequence");
                }
                reader = new DERReader(pair.b);
                reader.read(); // version;
                BigInteger modulus = new BigInteger(reader.read().b);
                reader.read(); // publicExponent
                BigInteger privateExponent = new BigInteger(reader.read().b);
                key = factory.generatePrivate(new RSAPrivateKeySpec(modulus, privateExponent)); // PKCS#5
            }
        }

        // load certificates(.crt)
        Certificate[] chain = certPaths.flatMap(path -> {
            try (InputStream in = Files.newInputStream(Paths.get(path))) {
                return CertificateFactory.getInstance("X.509").generateCertificates(in).stream();
            } catch (IOException | CertificateException e) {
                e.printStackTrace();
            }
            return Stream.empty();
        }).toArray(Certificate[]::new);

        // create key store
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] password = {};
        store.load(null, password);
        store.setKeyEntry("", key, password, chain);

        // setup SSL context
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }

    /**
     * reader for DER format
     */
    static class DERReader {

        /**
         * index
         */
        int index;

        /**
         * bytes
         */
        byte[] bytes;

        /**
         * constructor
         *
         * @param bytes bytes
         */
        public DERReader(byte[] bytes) {
            index = 0;
            this.bytes = bytes;
        }

        /**
         * @return tag, value
         */
        public Pair<Integer, byte[]> read() {
            int tag = bytes[index++];
            int length = bytes[index++];
            int index0 = index;
            if ((length & ~0x7f) != 0) {
                index += length & 0x7f;
                length = new BigInteger(1, Arrays.copyOfRange(bytes, index0, index)).intValue();
                index0 = index;
            }
            index += length;
            return Tool.pair(tag, Arrays.copyOfRange(bytes, index0, index));
        }
    }
}
