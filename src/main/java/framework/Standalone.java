package framework;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Try.TryConsumer;
import framework.annotation.Route.Method;

/**
 * Servlet implementation
 */
@SuppressWarnings("restriction")
public class Standalone {

    /**
     * @param args not use
     */
    @SuppressFBWarnings({ "REC_CATCH_EXCEPTION" })
    public static void main(String[] args) {

        // setup
        String contextPath = Sys.context_path;
        new ApplicationImpl(contextPath).setup(ResponseImpl::new);
        Logger logger = Tool.getLogger();
        Executor executor = Executors.newWorkStealingPool();
        HttpHandler handler = exchange -> {
            try (Defer<Request> request = new Defer<>(new RequestImpl(exchange), r -> Request.CURRENT.remove());
                    Defer<Lazy<Session>> session = new Defer<>(new Lazy<>(() -> {
                        Session s = new SessionImpl(exchange);
                        Session.CURRENT.set(s);
                        return s;
                    }), s -> s.ifGot(i -> Session.CURRENT.remove()).close())) {
                Request.CURRENT.set(request.get());
                Application.current().get().handle(request.get(), session.get());
            } catch (Exception e) {
                logger.log(Level.WARNING, "500", e);
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        };

        // start HTTP server
        Sys.http_port.ifPresent(port -> {
            try {
                HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
                http.setExecutor(executor);
                http.createContext(contextPath, handler);
                http.start();
                logger.info("http server started on port " + port);
            } catch (IOException e) {
                logger.log(Level.WARNING, "http sever setup error", e);
            }
        });

        // start HTTPS server
        Sys.https_port.ifPresent(port -> {
            try {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(port), 0);
                String keyPath = Sys.https_key_file.orElse(null);
                Stream<String> certPaths = Sys.https_cert_files.stream();
                https.setHttpsConfigurator(new HttpsConfigurator(createSSLContext(keyPath, certPaths)));
                https.setExecutor(executor);
                https.createContext(contextPath, handler);
                https.start();
                logger.info("https server started on port " + port);
            } catch (IOException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException
                    | InvalidKeySpecException e) {
                logger.log(Level.WARNING, "https server setup error", e);
            }
        });

        // start H2 DB console
        Sys.h2_port.ifPresent(port -> {
            try {
                File config = new File(Tool.suffix(System.getProperty("java.io.tmpdir"), File.separator) + ".h2.server.properties");
                List<String> lines = new ArrayList<>();
                lines.add("webAllowOthers=" + Sys.h2_allow_remote);
                lines.add("webPort=" + port);
                lines.add("webSSL=" + Sys.h2_ssl);
                AtomicInteger index = new AtomicInteger(-1);
                Sys.properties.entrySet().stream().sorted((a, b) -> ((String) b.getKey()).compareTo((String) a.getKey()))
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
                logger.info("h2 console started on port " + port);
            } catch (Exception e) {
                logger.log(Level.WARNING, "h2 error", e);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(Application.current().get()::shutdown));
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

    /**
     * Application implementation
     */
    static class ApplicationImpl extends Application {
        /**
         * Context path
         */
        String contextPath;
        /**
         * Attributes
         */
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        /**
         * @param contextPath context path
         */
        @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
        ApplicationImpl(String contextPath) {
            this.contextPath = contextPath;
            CURRENT = this;
        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> attributes.get(name)));
        }

        @Override
        public void setAttr(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttr(String name) {
            attributes.remove(name);
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }
    }

    /**
     * Session implementation
     */
    public static class SessionImpl extends Session implements AutoCloseable {

        /**
         * session cookie name
         */
        static final String NAME = Sys.session_name;

        /**
         * session id
         */
        String id;

        /**
         * Attributes
         */
        Map<String, Serializable> attributes;

        /**
         * saved attributes
         */
        boolean saved = false;

        /**
         * @param exchange exchange
         */
        @SuppressWarnings("unchecked")
        SessionImpl(HttpExchange exchange) {
            id = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie")).map(s -> Stream.of(s.split("\\s*;\\s*")).map(t -> t.split("=", 2))
                    .filter(a -> NAME.equalsIgnoreCase(a[0])).findAny().map(a -> a[1].replaceFirst("\\..*$", "")).orElse(null)).orElse(null);
            if (id == null) {
                id = Tool.hash("" + hashCode() + System.currentTimeMillis() + exchange.getRemoteAddress() + Math.random());
                exchange.getResponseHeaders().add("Set-Cookie", createSetCookie(NAME, id + Sys.cluster_suffix, null, -1, null,
                        Application.current().map(Application::getContextPath).orElse(null), false, true));
            } else {
                try (Db db = Db.connect()) {
                    int timeout = Sys.session_timeout_minutes;
                    if (timeout > 0) {
                        db.from("t_session").where("last_access", "<", LocalDateTime.now().minusMinutes(timeout)).delete();
                    }
                    db.select("value").from("t_session").where("id", id).row(rs -> {
                        try (InputStream in = rs.getBinaryStream(1)) {
                            if (in != null) {
                                ObjectInputStream o = new ObjectInputStream(in);
                                attributes = (Map<String, Serializable>) o.readObject();
                            }
                        }
                    });
                }
            }
            if (attributes == null) {
                attributes = new ConcurrentHashMap<>();
            }
        }

        /**
         * @param name cookie name
         * @param value cookie value
         * @param expires expires date time
         * @param maxAge max age seconds(enabled if positive)
         * @param domain scope suffix
         * @param path scope path
         * @param secure allow only HTTPS
         * @param httpOnly reject script access
         * @return set-cookie value(without Set-Cookie:)
         */
        static String createSetCookie(String name, String value, ZonedDateTime expires, long maxAge, String domain, String path, boolean secure,
                boolean httpOnly) {
            StringBuilder result = new StringBuilder(name + "=" + value);
            Optional.ofNullable(expires).map(DateTimeFormatter.RFC_1123_DATE_TIME::format).ifPresent(s -> result.append("; Expires=").append(s));
            if (maxAge > 0) {
                result.append("; Max-Age=").append(maxAge);
            }
            Optional.ofNullable(domain).ifPresent(s -> result.append("; Domain=").append(s));
            Optional.ofNullable(path).ifPresent(s -> result.append("; Path=").append(s));
            if (secure) {
                result.append("; Secure");
            }
            if (httpOnly) {
                result.append("; HttpOnly");
            }
            return result.toString();
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> attributes.get(name)));
        }

        @Override
        public void setAttr(String name, Serializable value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttr(String name) {
            attributes.remove(name);
        }

        /**
         * save session attributes
         */
        public void save() {
            if (saved) {
                return;
            }
            try (Db db = Db.connect(); ByteArrayOutputStream out = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(out)) {
                o.writeObject(attributes);
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                if (db.preparedQuery("SELECT * FROM t_session WHERE id = ? FOR UPDATE", ps -> {
                    ps.setString(1, id);
                    return Tool.array(id);
                }, rs -> db.prepare("UPDATE t_session SET value = ?, last_access = ? WHERE id = ?", ps -> {
                    ps.setBytes(1, out.toByteArray());
                    ps.setTimestamp(2, now);
                    ps.setString(3, id);
                    ps.executeUpdate();
                    return Tool.array("(blob)", now, id);
                })) <= 0) {
                    db.prepare("INSERT INTO t_session(id, value, last_access) VALUES(?, ?, ?)", ps -> {
                        ps.setString(1, id);
                        ps.setBytes(2, out.toByteArray());
                        ps.setTimestamp(3, now);
                        ps.executeUpdate();
                        return Tool.array(id, "(blob)", now);
                    });
                }
                saved = true;
            } catch (IOException e) {
                Logger.getLogger(Session.class.getName()).log(Level.SEVERE, null, e);
            }
        }

        @Override
        public void close() throws Exception {
            save();
        }
    }

    /**
     * Request implementation
     */
    static class RequestImpl extends Request {

        /**
         * exchange
         */
        final HttpExchange exchange;
        /**
         * request headers
         */
        final Headers requestHeaders;
        /**
         * parameters
         */
        final Map<String, List<String>> parameters = new LinkedHashMap<>(); // name, value
        /**
         * files
         */
        final Map<String, Tuple<byte[], File>> files = new LinkedHashMap<>(); // file name, file content
        /**
         * attributes
         */
        final Map<String, Object> attributes = new LinkedHashMap<>(); // name, value
        /**
         * path
         */
        final String path;

        /**
         * @param exchange exchange
         * @throws IOException IO error
         */
        RequestImpl(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
            Logger logger = Tool.getLogger();
            String p = exchange.getRequestURI().getPath();
            String r = Application.current().map(Application::getContextPath).orElse("");
            path = p.length() <= r.length() ? "/" : p.substring(r.length());
            requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");
            // query parameter
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                parse(parameters, new Scanner(query));
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (contentType.startsWith("application/x-www-form-urlencoded")) {
                    try (InputStream in = exchange.getRequestBody()) {
                        parse(parameters, new Scanner(in, StandardCharsets.ISO_8859_1.name()));
                    }
                } else if (contentType.startsWith("multipart/form-data")) {
                    final String boundary = "--" + new KeyValueAttr("Content-Type:" + contentType).attr.get("boundary");
                    try (InputStream in = exchange.getRequestBody()) {
                        readLine(in);// first boundary
                        loop: for (;;) {
                            String name = null;
                            String filename = null;
                            int length = 0;
                            // read headers
                            for (;;) {
                                byte[] line = readLine(in);
                                if (line == null) {
                                    break loop;
                                }
                                if (line.length <= 2) {
                                    break;
                                }
                                KeyValueAttr header = new KeyValueAttr(new String(line, 0, line.length - 2, StandardCharsets.UTF_8));
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
                            if (name == null) {
                                continue;
                            }
                            if (filename == null) {// parameter value
                                Tuple<byte[], File> pair = readBody(in, boundary);
                                if (pair.r != null) {
                                    logger.config(pair.r + " deleted " + pair.r.delete());
                                }
                                if (pair.l == null) {
                                    logger.info("413 payload too large");
                                    break loop;
                                }
                                Tool.add(parameters, name, new String(pair.l, StandardCharsets.UTF_8));
                            } else {
                                Tool.add(parameters, name, filename);
                                if (length > 0) {
                                    if (length < fileSizeThreshold) {
                                        byte[] bytes = new byte[length];
                                        int n = in.read(bytes);
                                        files.put(filename, Tuple.of(Arrays.copyOfRange(bytes, 0, n), null));
                                    } else {
                                        File f = File.createTempFile("upload", "file", Tool.string(Sys.upload_folder).map(File::new).orElse(null));
                                        f.deleteOnExit();
                                        try (FileOutputStream out = new FileOutputStream(f); FileChannel to = out.getChannel()) {
                                            to.transferFrom(Channels.newChannel(in), 0, length);
                                        }
                                        logger.info("saved " + f + " " + length + "bytes");
                                        files.put(filename, Tuple.of(null, f));
                                    }
                                } else if (!filename.isEmpty()) {
                                    files.put(filename, readBody(in, boundary));
                                }
                            }
                        }
                    }
                } else {
                    try (InputStream in = exchange.getRequestBody()) {
                        for (;;) {
                            byte[] bytes = readLine(in);
                            if (bytes == null) {
                                break;
                            }
                            System.out.print(new String(bytes, StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> attributes.get(name)));
        }

        @Override
        public void setAttr(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttr(String name) {
            attributes.remove(name);
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Method getMethod() {
            return Enum.valueOf(Method.class, exchange.getRequestMethod());
        }

        @Override
        public Map<String, Tuple<byte[], File>> getFiles() {
            return files;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return requestHeaders;
        }

        /**
         * on memory when less than this value
         */
        @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
        public static int fileSizeThreshold = 32768;

        /**
         * uploadable max size
         */
        public static int maxFileSize = 50 * 1024 * 1024;

        /**
         * @param in input
         * @param boundary boundary(include prefix --)
         * @return body(bytes or file)
         * @throws IOException IO error
         */
        static Tuple<byte[], File> readBody(InputStream in, String boundary) throws IOException {
            ByteArrayOutputStream lines = new ByteArrayOutputStream();
            OutputStream out = lines;
            try {
                long size = 0;
                boolean crlf = false;
                File f = null;
                for (;;) {
                    if (f == null && size >= fileSizeThreshold) {
                        f = File.createTempFile("upload", "file", Tool.string(Sys.upload_folder).map(File::new).orElse(null));
                        f.deleteOnExit();
                        out = new BufferedOutputStream(Files.newOutputStream(f.toPath()));
                        out.write(lines.toByteArray());
                    }
                    byte[] line = readLine(in);
                    if (line == null || startsWith(line, boundary)) {
                        break;
                    }
                    int length = line.length;
                    if (crlf) {
                        out.write('\r');
                        size++;
                        out.write('\n');
                        size++;
                    }
                    crlf = length >= 2 && line[length - 2] == '\r' && line[length - 1] == '\n';
                    if (crlf) {
                        out.write(line, 0, length - 2);
                        size += length - 2;
                    } else {
                        out.write(line);
                        size += length;
                    }
                }
                if (out == lines) {
                    return Tuple.of(lines.toByteArray(), null);
                }
                Tool.getLogger().info("saved " + f + " " + size + "bytes");
                return Tuple.of(null, f);
            } finally {
                lines.close();
                out.close();
            }
        }

        /**
         * key &amp; value &amp; attributes
         */
        static class KeyValueAttr {

            /**
             * decode
             */
            public static Function<String, String> decode = Function.identity();
            /**
             * normalize
             */
            public static Function<String, String> normalize = String::toLowerCase;
            /**
             * pair separator pattern
             */
            public static Pattern PAIR_SEPARATOR = Pattern.compile("\\s*:\\s*");
            /**
             * attribute separator pattern
             */
            public static Pattern ATTRIBUTE_SEPARATOR = Pattern.compile("\\s*;\\s*");
            /**
             * attribute pair separator pattern
             */
            public static Pattern ATTRIBUTE_PAIR_SEPARATOR = Pattern.compile("\\s*=\\s*");
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
                String[] pair = PAIR_SEPARATOR.split(text, 2);
                if (pair == null || pair.length <= 0) {
                    key = null;
                    value = null;
                    attr = null;
                    return;
                }
                key = normalize.apply(pair[0]);
                if (pair.length > 1) {
                    String[] valueAttr = ATTRIBUTE_SEPARATOR.split(pair[1]);
                    if (valueAttr != null && valueAttr.length > 0) {
                        value = decode.apply(valueAttr[0]);
                        if (valueAttr.length > 1) {
                            attr = Stream.of(valueAttr).skip(1).filter(Tool.notEmpty).map(i -> ATTRIBUTE_PAIR_SEPARATOR.split(i, 2)).collect(LinkedHashMap::new,
                                    (map, a) -> map.put(normalize.apply(a[0]), a.length < 1 ? "" : decode.apply(Tool.trim("\"", a[1], "\""))), Map::putAll);
                            return;
                        }
                    } else {
                        value = "";
                    }
                } else {
                    value = "";
                }
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
                    Tool.add(parameters, URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()),
                            pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()) : "");
                }
            }));
        }

        /**
         * @param in in
         * @return bytes
         * @throws IOException IO error
         */
        public static byte[] readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            for (;;) {
                int c = in.read();
                if (c == -1) {
                    if (line.size() <= 0) {
                        return null;
                    }
                    break;
                }
                line.write(c);
                if (c == '\n') {
                    break;
                }
                if (c == '\r') {
                    c = in.read();
                    if (c != -1) {
                        line.write(c);
                    }
                    break;
                }
            }
            return line.toByteArray();
        }

        /**
         * @param source source
         * @param prefix prefix
         * @return true if prefix starts with source
         */
        public static boolean startsWith(byte[] source, String prefix) {
            byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > source.length) {
                return false;
            }
            int i = 0;
            for (byte b : bytes) {
                if (source[i] != b) {
                    return false;
                }
                i++;
            }
            return true;
        }

        @Override
        public Map<String, List<String>> getParameters() {
            return parameters;
        }
    }

    /**
     * Response implementation
     */
    static class ResponseImpl extends Response {

        @Override
        public void writeResponse(Consumer<Supplier<OutputStream>> writeBody) {
            HttpExchange exchange = ((RequestImpl) Request.current().get()).exchange;
            TryConsumer<Long> action = contentLength -> {
                Session.current().map(s -> (SessionImpl) s).ifPresent(SessionImpl::save);
                Sys.headers.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                if (headers != null) {
                    headers.forEach((key, values) -> values.forEach(value -> exchange.getResponseHeaders().add(key, value)));
                }
                exchange.sendResponseHeaders(status.code, contentLength);
            };
            if (content == null) {
                Try.c(action).accept(-1L);
            } else {
                writeBody.accept(Try.s(() -> {
                    action.accept(headers == null ? 0L : Tool.getFirstValue(headers, "Content-Length").flatMap(Tool::longInteger).orElse(0L));
                    return exchange.getResponseBody();
                }));
            }
            exchange.close();
        }

        @Override
        public String toString() {
            return Request.current().map(i -> ((RequestImpl) i).exchange)
                    .map(r -> "-> " + r.getProtocol() + " " + r.getResponseCode() + " " + Tool.string(r.getResponseHeaders().getFirst("Content-Type")).orElse("")).orElse("");
        }
    }
}
