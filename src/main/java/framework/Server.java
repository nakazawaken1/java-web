package framework;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Db.Setup;
import framework.annotation.Http;
import framework.annotation.Job;
import framework.annotation.Only;
import framework.annotation.Query;

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
     * application scope object
     */
    transient static Application application;

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
    @SuppressFBWarnings({ "LI_LAZY_INIT_STATIC", "LI_LAZY_INIT_UPDATE_STATIC", "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD" })
    public void init(ServletConfig config) throws ServletException {

        /* check to enabled of method parameters name */
        try {
            if (!"config".equals(Server.class.getMethod("init", ServletConfig.class).getParameters()[0].getName())) {
                throw new ServletException("must to enable compile option `-parameters`");
            }
        } catch (NoSuchMethodException | SecurityException e) {
        }

        /* log setup */
        Config.startupLog();

        /* create application scope object */
        if (application == null) {
            application = new Application(config.getServletContext());
            logger.info(application.toString());
        }

        /* setup routing */
        if (table == null) {
            table = Tool.getClasses("app.controller").flatMap(c -> Stream.of(c.getDeclaredMethods()).map(m -> Tool.pair(m, m.getAnnotation(Http.class)))
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
            logger.info(Tool.print(writer -> {
                writer.println("---- routing ----");
                table.forEach((path, pair) -> writer.println(path + " -> " + pair.a.getName() + "." + pair.b.getName()));
            }));
        }

        /* database setup */
        Db.setup(Config.db_setup.enumOf(Setup.class));

        /* job scheduler setup */
        Job.Scheduler.setup(Tool.getClasses("app.controller").toArray(Class<?>[]::new));
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
        Request.request.set((HttpServletRequest) req);
        Request.response.set((HttpServletResponse) res);
        try {
            Request request = new Request();
            logger.info(request.toString());

            /* no slash root access */
            if (request.path == null) {
                new Response(r -> {
                    r.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                    r.setHeader("Location", application.getContextPath());
                }).flush();
                return;
            }
            Session session = new Session();
            session.raw.setMaxInactiveInterval(Config.app_session_timeout_seconds.integer());

            /* action */
            Pair<Class<?>, Method> pair = table.get(request.path);
            if (pair != null) {
                Method method = pair.b;
                Http http = method.getAnnotation(Http.class);
                if (http == null || (http.value().length > 0 && !Arrays.asList(http.value()).contains(request.getMethod()))) {
                    Response.error(400).flush();
                    return;
                }
                Only only = method.getAnnotation(Only.class);
                boolean forbidden = only != null && !session.isLoggedIn();
                if (!forbidden && only != null && only.value().length > 0) {
                    forbidden = !session.getAccount().hasAnyRole(only.value());
                }
                if (forbidden) {
                    session.setAttr("alert", "アクセス権限がありません。権限のあるアカウントでログインしてください");
                    Response.redirect(application.getContextPath()).flush();
                    return;
                }
                request.raw.setCharacterEncoding(StandardCharsets.UTF_8.name());
                try (Closer<Db> db = new Closer<>()) {
                    try {
                        ((Response) method.invoke(Modifier.isStatic(method.getModifiers()) ? null : pair.a.newInstance(),
                                Stream.of(method.getParameters()).map(p -> {
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
                                    if (Db.class.isAssignableFrom(type)) {
                                        return db.set(Db.connect());
                                    }
                                    if (p.getAnnotation(Query.class) != null) {
                                        return parseValue(type, p, request.raw.getParameter(p.getName()));
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
            if (Config.toURL(Config.app_view_folder.text(), request.path).isPresent()) {
                Response.file(request.path).flush();
                return;
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
    private Object parseValue(Type type, Parameter p, String value) {
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
     * reader for PEM format
     */
    static class PEMReader {
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
        public PEMReader(byte[] bytes) {
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
            if ((length & ~0x7F) != 0) {
                index += length & 0x7f;
                length = new BigInteger(1, Arrays.copyOfRange(bytes, index0, index)).intValue();
                index0 = index;
            }
            index += length;
            return Tool.pair(tag, Arrays.copyOfRange(bytes, index0, index));
        }
    }

    /**
     * @param args http-port https-port .key .crt...
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InvalidKeySpecException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     * @throws CertificateException
     * @throws NoSuchProviderException
     */
    public static void main(String[] args) throws KeyStoreException, NoSuchAlgorithmException, InvalidKeySpecException, IOException, UnrecoverableKeyException,
            KeyManagementException, CertificateException, NoSuchProviderException {

        if (args.length < 4) {
            System.err.println("usage: (command) http-port https-port host.key host.crt...");
            return;
        }
        int httpPort = Integer.parseInt(args[0]);
        int httpsPort = Integer.parseInt(args[1]);
        String keyPath = args[2];
        Stream<String> certPaths = Stream.of(args).skip(3);

        // load private key(.key)
        PrivateKey key;
        try (Stream<String> lines = Files.lines(Paths.get(keyPath))) {
            String text = lines.filter(line -> !line.isEmpty() && !line.startsWith("--") && line.indexOf(':') < 0).collect(Collectors.joining());
            byte[] bytes = Base64.getMimeDecoder().decode(text);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            try {
                key = factory.generatePrivate(new PKCS8EncodedKeySpec(bytes)); // PKCS#8
            } catch (InvalidKeySpecException e) {
                PEMReader reader = new PEMReader(bytes);
                Pair<Integer, byte[]> pair = reader.read(); // sequence
                if ((pair.a & 0x1f) != 0x10) {
                    throw new InvalidKeySpecException("first part is not sequence");
                }
                reader = new PEMReader(pair.b);
                reader.read(); // version;
                key = factory.generatePrivate(new RSAPrivateCrtKeySpec(new BigInteger(reader.read().b), new BigInteger(reader.read().b),
                        new BigInteger(reader.read().b), new BigInteger(reader.read().b), new BigInteger(reader.read().b), new BigInteger(reader.read().b),
                        new BigInteger(reader.read().b), new BigInteger(reader.read().b))); // PKCS#1
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

        // start HTTPS server
        // ExecutorService executor = Executors.newWorkStealingPool();
        // try (ServerSocket listener = context.getServerSocketFactory().createServerSocket()) {
        // listener.setReuseAddress(true);
        // int port = Integer.parseInt(args[0]);
        // listener.bind(new InetSocketAddress(port));
        // Logger.getGlobal().info("https server listening on port " + port);
        // for (;;) {
        // Socket socket = listener.accept();
        // if(socket.isInputShutdown()) {
        // continue;
        // }
        // executor.submit(() -> {
        // try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
        // byte[] buffer = new byte[4096];
        // if(socket.isOutputShutdown()) {
        // return;
        // }
        // out.write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        // if(socket.isInputShutdown()) {
        // return;
        // }
        // int n = in.read(buffer);
        // out.write(buffer, 0, n);
        // } catch (IOException e) {
        // Logger.getGlobal().log(Level.WARNING, "socket error", e);
        // }
        // });
        // }
        // } finally {
        // executor.shutdown();
        // }

        HttpHandler handler = new HttpHandler() {
            @Override
            public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
                try {
                    URI uri = exchange.getRequestURI();
                    Headers requestHeaders = exchange.getRequestHeaders();
                    String contentType = requestHeaders.getFirst("Content-Type");
                    logger.info(exchange.getRequestMethod() + " " + uri.getPath() + " " + contentType);
                    String query = uri.getRawQuery();
                    Map<String, List<String>> parameters = new LinkedHashMap<>();
                    List<byte[]> files = new ArrayList<>();
                    if (query != null) {
                        parse(parameters, new Scanner(query));
                    }
                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        if (contentType.startsWith("application/x-www-form-urlencoded")) {
                            try (InputStream in = exchange.getRequestBody()) {
                                parse(parameters, new Scanner(in, StandardCharsets.ISO_8859_1.name()));
                            }
                        } else if (contentType.startsWith("multipart/form-data")) {
                            String boundary = new KeyValueAttr("Content-Type:" + contentType).attr.get("boundary");
                            try (InputStream in = exchange.getRequestBody();
                                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.ISO_8859_1);
                                    BufferedReader scanner = new BufferedReader(reader)) {
                                String name = null;
                                String filename = null;
                                int length = 0;
                                for (;;) {
                                    String line = scanner.readLine();
                                    if (line == null || line.isEmpty()) {
                                        break;
                                    }
                                    KeyValueAttr header = new KeyValueAttr(line);
                                    if (header.key == null) {
                                        continue;
                                    }
                                    switch (header.key) {
                                    case "content-disposition":
                                        name = header.attr.get("name");
                                        filename = header.attr.get("filename");
                                        break;
                                    case "content-length":
                                        length = Integer.parseInt(header.value);
                                        break;
                                    }
                                }
                                if (filename == null) {
                                    StringBuilder lines = new StringBuilder();
                                    for (;;) {
                                        String line = scanner.readLine();
                                        if (line == null || line.startsWith("--" + boundary)) {
                                            break;
                                        }
                                        lines.append(line).append("\n");
                                    }
                                    add(parameters, name, lines.toString());
                                } else {
                                    add(parameters, name, filename);
                                    if (length > 0) {
                                        byte[] bytes = new byte[length];
                                        int n = in.read(bytes);
                                        if (n > 0) {
                                            files.add(bytes);
                                        }
                                    } else {
                                        StringBuilder lines = new StringBuilder();
                                        for (;;) {
                                            String line = scanner.readLine();
                                            if (line == null || line.startsWith("--" + boundary)) {
                                                break;
                                            }
                                            lines.append(line).append("\n");
                                        }
                                        files.add(lines.toString().getBytes(StandardCharsets.UTF_8));
                                    }
                                }
                            }
                        }
                    }
                    try (OutputStream out = exchange.getResponseBody()) {
                        StringBuilder response = new StringBuilder(
                                "<form method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"file\"/><input type=\"submit\"/></form>");
                        response.append(exchange.getRequestMethod()).append(' ').append(exchange.getRequestURI().getPath());
                        parameters.forEach((k, v) -> response.append(k).append('=').append(v));
                        files.forEach(i -> response.append(";" + i.length));
                        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, bytes.length);
                        out.write(bytes);
                    }
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "500", e);
                }
            }
        };
        Executor executor = Executors.newWorkStealingPool();

        if (httpsPort >= 0) {
            HttpsServer https = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(context));
            https.setExecutor(executor);
            https.createContext("/", handler);
            https.start();
            logger.info("https server started on port " + httpsPort);
        }

        if (httpPort >= 0) {
            HttpServer http = HttpServer.create(new InetSocketAddress(httpPort), 0);
            http.setExecutor(executor);
            http.createContext("/", handler);
            http.start();
            logger.info("http server started on port " + httpPort);
        }
    }

    /**
     * key & value & attributes
     */
    static class KeyValueAttr {
        /**
         * pair separator pattern
         */
        public static final Pattern PAIR_SEPARATOR = Pattern.compile("\\s*:\\s*");
        /**
         * attribute separator pattern
         */
        public static final Pattern ATTRIBUTE_SEPARATOR = Pattern.compile("\\s*;\\s*");
        /**
         * attribute pair separator pattern
         */
        public static final Pattern ATTRIBUTE_PAIR_SEPARATOR = Pattern.compile("\\s*=\\s*");
        /**
         * key
         */
        public final String key;
        /**
         * value
         */
        public final String value;
        /**
         * attributes
         */
        public final Map<String, String> attr;

        /**
         * constructor
         * 
         * @param text text for parse
         */
        public KeyValueAttr(String text) {
            String[] pair = PAIR_SEPARATOR.split(text);
            if (pair == null || pair.length <= 0) {
                key = null;
                value = null;
                attr = null;
                return;
            }
            key = pair[0].toLowerCase();
            if (pair.length > 1) {
                String[] valueAttr = ATTRIBUTE_SEPARATOR.split(pair[1]);
                if (valueAttr != null && valueAttr.length > 0) {
                    value = valueAttr[0];
                    attr = Stream.of(valueAttr).skip(1).map(i -> ATTRIBUTE_PAIR_SEPARATOR.split(i)).collect(LinkedHashMap::new,
                            (map, a) -> map.put(a[0].toLowerCase(), Tool.trim("\"", a[1], "\"")), Map::putAll);
                    return;
                }
            }
            value = "";
            attr = Collections.emptyMap();
        }
    }

    /**
     * @param parameters parameters
     * @param scanner scanner
     */
    public static void parse(Map<String, List<String>> parameters, Scanner scanner) {
        scanner.useDelimiter("[&]");
        scanner.forEachRemaining(Try.c(part -> {
            String[] pair = part.split("[=]");
            if (pair.length > 0) {
                add(parameters, URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()),
                        pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()) : "");
            }
        }));
    }

    /**
     * @param map map
     * @param key key
     * @param value value
     */
    public static void add(Map<String, List<String>> map, String key, String value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            List<String> list = new ArrayList<>();
            list.add(value);
            map.put(key, list);
        }
    }
}
