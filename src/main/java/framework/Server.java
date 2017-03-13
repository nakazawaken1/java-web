package framework;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
import framework.annotation.Route;
import framework.annotation.Job;
import framework.annotation.Only;
import framework.annotation.Param;

/**
 * Servlet implementation class
 */
@SuppressWarnings("restriction")
@WebServlet("/")
public class Server implements Servlet {

    /**
     * logger
     */
    transient Logger logger = Tool.getLogger();

    /**
     * routing table{path: {class: method}}
     */
    transient static Map<String, Tuple<Class<?>, Method>> table;

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
     */
    @Override
    @SuppressFBWarnings({ "LI_LAZY_INIT_UPDATE_STATIC", "LI_LAZY_INIT_STATIC" })
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
        try (Defer<Request> request = new Defer<>(new Request.ForServlet((HttpServletRequest) req, (HttpServletResponse) res), r -> Request.CURRENT.remove());
                Defer<Lazy<Session>> session = new Defer<>(new Lazy<>(() -> {
                    Session s = new Session.ForServlet(((HttpServletRequest) req).getSession());
                    Session.CURRENT.set(s);
                    return s;
                }), s -> s.ifGot(i -> Session.CURRENT.remove()).close())) {
            Request.CURRENT.set(request.get());
            logger = Tool.getLogger();
            handle(request.get(), session.get());
        }
    }

    /**
     * setup
     *
     * @param applicationGetter applicationGetter
     * @param responseCreator responseCreator
     */
    @SuppressFBWarnings({ "LI_LAZY_INIT_STATIC" })
    void setup(Lazy<Application> applicationGetter, Supplier<ResponseCreator> responseCreator) {

        /* check to enabled of method parameters name */
        try {
            if (!"config".equals(Server.class.getMethod("init", ServletConfig.class).getParameters()[0].getName())) {
                throw new RuntimeException("must to enable compile option `-parameters`");
            }
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }

        /* log setup */
        Config.startupLog();

        /* create application scope object */
        if (!Application.current().isPresent()) {
            Application.CURRENT.set(applicationGetter.get());
            logger.info(Application.current().get().toString());
        }

        /* setup for response creator */
        if (Response.create == null) {
            Response.create = responseCreator;
        }

        /* setup routing */
        if (table == null) {
            table = new HashMap<>();
            Config.app_controller_packages.stream().forEach(p -> {
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
            logger.info(Tool.print(writer -> {
                writer.println("---- routing ----");
                table.forEach((path, pair) -> writer.println(path + " -> " + pair.l.getName() + "." + pair.r.getName()));
            }));
        }

        /* database setup */
        Db.setup(Config.db_setup.enumOf(Setup.class));

        /* job scheduler setup */
        List<Class<?>> cs = new ArrayList<>();
        Config.app_job_packages.stream().forEach(p -> {
            try (Stream<Class<?>> classes = Tool.getClasses("app.controller")) {
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
     * @throws ServletException server error
     * @throws IOException IO error
     */
    void handle(Request request, Lazy<Session> session) throws ServletException, IOException {
        logger.info(request.toString());

        Application application = Application.current().get();

        /* no slash root access */
        if (request.getPath() == null) {
            Response.redirect(application.getContextPath(), 301).flush();
            return;
        }

        /* action */
        Tuple<Class<?>, Method> pair = table.get(request.getPath());
        if (pair != null) {
            Method method = pair.r;
            Route http = method.getAnnotation(Route.class);
            if (http == null || http.value().length > 0 && !Arrays.asList(http.value()).contains(request.getMethod())) {
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
                    ((Response) method.invoke(Modifier.isStatic(method.getModifiers()) ? null : pair.l.newInstance(),
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
                                if (p.getAnnotation(Param.class) != null) {
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
                switch (i.getProtocol()) {
                case "file":
                    return !new File(i.toURI()).isDirectory();
                case "jar":
                    return !((JarURLConnection) i.openConnection()).getJarEntry().isDirectory();
                default:
                    return false;
                }
            }, (e, i) -> false)).isPresent()) {
                Response.redirect(Tool.trim(null, application.getContextPath(), "/") + Tool.suffix(request.getPath(), "/") + "index.html", 301).flush();
            } else {
                Response.file(request.getPath()).flush();
            }
            return;
        }

        /* */
        if (Arrays.asList(".css", ".js").contains(request.getExtension())) {
            Response.text("/*" + request.getPath() + " not found*/").contentType(Tool.getContentType(request.getPath())).flush();
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
     * @param args not use
     */
    @SuppressFBWarnings({ "LI_LAZY_INIT_STATIC", "REC_CATCH_EXCEPTION" })
    public static void main(String[] args) {

        String contextPath = Config.app_context_path.text();
        int httpPort = Config.app_http_port.integer();
        int httpsPort = Config.app_https_port.integer();
        String keyPath = Config.app_https_key_file.get().orElse(null);
        Stream<String> certPaths = Config.app_https_cert_files.stream();

        Server server = new Server();

        server.setup(new Lazy<>(() -> new Application.ForServer(contextPath)), Response.ForServer::new);

        // start HTTPS server
        HttpHandler handler = exchange -> {
            try (Defer<Request> request = new Defer<>(new Request.ForServer(exchange), r -> Request.CURRENT.remove());
                    Defer<Lazy<Session>> session = new Defer<>(new Lazy<>(() -> {
                        Session s = new Session.ForServer(exchange);
                        Session.CURRENT.set(s);
                        return s;
                    }), s -> s.ifGot(i -> Session.CURRENT.remove()).close())) {
                Request.CURRENT.set(request.get());
                server.logger = Tool.getLogger();
                server.handle(request.get(), session.get());
            } catch (Exception e) {
                server.logger.log(Level.WARNING, "500", e);
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        };

        Executor executor = Executors.newWorkStealingPool();

        try {
            if (httpPort > 0) {
                HttpServer http = HttpServer.create(new InetSocketAddress(httpPort), 0);
                http.setExecutor(executor);
                http.createContext(contextPath, handler);
                http.start();
                server.logger.info("http server started on port " + httpPort);
            }

            if (httpsPort > 0) {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(createSSLContext(keyPath, certPaths)));
                https.setExecutor(executor);
                https.createContext(contextPath, handler);
                https.start();
                server.logger.info("https server started on port " + httpsPort);
            }
        } catch (IOException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException
                | InvalidKeySpecException e) {
            server.logger.log(Level.WARNING, "setup error", e);
        }

        // start H2 DB console
        if (Config.app_h2_port.integer() > 0) {
            try {
                File config = new File(Tool.suffix(System.getProperty("java.io.tmpdir"), File.separator) + ".h2.server.properties");
                List<String> lines = new ArrayList<>();
                lines.add("webAllowOthers=" + Config.app_h2_allow_remote.isTrue());
                lines.add("webPort=" + Config.app_h2_port.text());
                lines.add("webSSL=" + Config.app_h2_ssl.isTrue());
                AtomicInteger index = new AtomicInteger(-1);
                Config.properties.entrySet().stream().sorted((a, b) -> ((String) b.getKey()).compareTo((String) a.getKey()))
                        .map(p -> Tuple.of((String) p.getKey(), (String) p.getValue())).filter(t -> t.l.startsWith("db") && t.r.startsWith("jdbc:"))
                        .map(t -> index.incrementAndGet() + "=" + t.l + "|" + Db.Type.fromUrl(t.r).driver + "|" + t.r.replace(":", "\\:").replace("=", "\\="))
                        .forEach(lines::add);
                Files.write(config.toPath(), lines, StandardCharsets.UTF_8);
                config.deleteOnExit();
                Object db = Tool.invoke("org.h2.tools.Server.createWebServer", Tool.array(String[].class),
                        new Object[] { Tool.array("-properties", config.getParent()) });
                Tool.invoke(db, "start", Tool.array());
                Runtime.getRuntime().addShutdownHook(
                        new Thread(Try.r(() -> Tool.invoke(db, "stop", Tool.array()), e -> Tool.getLogger().log(Level.WARNING, "h2 stop error", e))));
            } catch (Exception e) {
                server.logger.log(Level.WARNING, "h2 error", e);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
    }

    /**
     * @param keyPath key file
     * @param certPaths cert files
     * @return SSLContext SSL error
     * @throws IOException IO errror
     * @throws NoSuchAlgorithmException algorithm error
     * @throws InvalidKeySpecException key error
     * @throws KeyStoreException key error
     * @throws CertificateException cert error
     * @throws UnrecoverableKeyException key error
     * @throws KeyManagementException key error
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
                Tuple<Integer, byte[]> pair = reader.read(); // sequence
                if ((pair.l & 0x1f) != 0x10) {
                    throw new InvalidKeySpecException("first part is not sequence");
                }
                reader = new DERReader(pair.r);
                reader.read(); // version;
                BigInteger modulus = new BigInteger(reader.read().r);
                reader.read(); // publicExponent
                BigInteger privateExponent = new BigInteger(reader.read().r);
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
        public Tuple<Integer, byte[]> read() {
            int tag = bytes[index++];
            int length = bytes[index++];
            int index0 = index;
            if ((length & ~0x7f) != 0) {
                index += length & 0x7f;
                length = new BigInteger(1, Arrays.copyOfRange(bytes, index0, index)).intValue();
                index0 = index;
            }
            index += length;
            return Tuple.of(tag, Arrays.copyOfRange(bytes, index0, index));
        }
    }
}
