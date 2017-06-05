package framework;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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

import app.config.Sys;
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
    public static void main(String... args) {

        // setup
        String contextPath = Sys.context_path;
        Application application = new ApplicationImpl(contextPath);
        application.setup(ResponseImpl::new);
        Executor executor = Executors.newWorkStealingPool();
        HttpHandler handler = exchange -> {
            try (Defer<RequestImpl> request = new Defer<>(Tool.peek(new RequestImpl(exchange), Request.CURRENT::set), r -> Request.CURRENT.remove());
                    Defer<SessionImpl> session = new Defer<>(Tool.peek(new SessionImpl(exchange), Session.CURRENT::set), s -> Session.CURRENT.remove())) {
                application.handle(request.get(), session.get());
            } catch (Exception e) {
                Log.warning(e, () -> "500");
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
                Log.info("http server started on port " + port);
            } catch (IOException e) {
                Log.warning(e, () -> "http sever setup error");
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
                Log.info("https server started on port " + port);
            } catch (IOException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException
                    | InvalidKeySpecException e) {
                Log.warning(e, () -> "https server setup error");
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(application::shutdown));
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
            return Tool.of(attributes.containsKey(name) ? (T) attributes.get(name) : Reflector.getProperty(this, name, () -> null));
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
     * Session store interface
     */
    interface SessionStore extends AutoCloseable {

        /**
         * @param id Session id
         * @return Key values
         */
        Map<String, Serializable> load(String id);

        /**
         * @param id Session id
         * @param keyValues Save key-values
         * @param removeKeys Remove keys
         */
        void save(String id, Map<String, Serializable> keyValues, Set<String> removeKeys);
    }

    /**
     * Session store with database
     */
    public static class SessionStoreDb implements SessionStore {

        /**
         * Database
         */
        final Db db = Db.connect(Sys.Db.session_suffix);

        /*
         * (non-Javadoc)
         * 
         * @see framework.Standalone.SessionStore#set(java.lang.String, java.util.Map)
         */
        @Override
        public void save(String id, Map<String, Serializable> keyValues, Set<String> removeKeys) {
            if (!keyValues.isEmpty()) {
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                db.prepare("UPDATE t_session SET value = ?, last_access = ? WHERE id = ? AND name = ?", update -> {
                    update.setString(3, id);
                    List<String> namesUpdate = new ArrayList<>();
                    List<String> valuesUpdate = new ArrayList<>();
                    db.prepare("INSERT INTO t_session(id, name, value, last_access) VALUES(?, ?, ?, ?)", insert -> {
                        insert.setString(1, id);
                        List<String> namesInsert = new ArrayList<>();
                        List<String> valuesInsert = new ArrayList<>();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        keyValues.forEach(Try.biC((name, value) -> {
                            if (db.preparedQuery("SELECT * FROM t_session WHERE id = ? AND name = ? FOR UPDATE", select -> {
                                select.setString(1, id);
                                select.setString(2, name);
                                return Tool.array(id, name);
                            }, rs -> {
                                namesUpdate.add(name);
                                valuesUpdate.add(Objects.toString(value));
                                update.setBytes(1, Tool.serialize(value, out));
                                update.setTimestamp(2, now);
                                update.setString(4, name);
                                update.executeUpdate();
                            }) <= 0) {
                                namesInsert.add(name);
                                valuesInsert.add(Objects.toString(value));
                                insert.setString(2, name);
                                insert.setBytes(3, Tool.serialize(value, out));
                                insert.setTimestamp(4, now);
                                insert.executeUpdate();
                            }
                        }));
                        return namesInsert.isEmpty() ? null : Tool.array(id, namesInsert, valuesInsert, now);
                    });
                    return namesUpdate.isEmpty() ? null : Tool.array(valuesUpdate, now, id, namesUpdate);
                });
            }
            if (!removeKeys.isEmpty()) {
                db.prepare("DELETE FROM t_session WHERE id = ? AND name = ?", delete -> {
                    delete.setString(1, id);
                    removeKeys.forEach(Try.c(name -> {
                        delete.setString(2, name);
                        delete.executeUpdate();
                    }));
                    return Tool.array(id, removeKeys);
                });
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.AutoCloseable#close()
         */
        @Override
        public void close() throws Exception {
            db.close();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Standalone.SessionStore#load(java.lang.String)
         */
        @Override
        public Map<String, Serializable> load(String id) {
            Map<String, Serializable> map = new ConcurrentHashMap<>();
            int timeout = Sys.session_timeout_minutes;
            if (timeout > 0) {
                db.from("t_session").where("last_access", "<", LocalDateTime.now().minusMinutes(timeout)).delete();
            }
            db.select("name", "value").from("t_session").where("id", id).rows(rs -> {
                try (InputStream in = rs.getBinaryStream(2); ObjectInputStream i = new ObjectInputStream(in)) {
                    if (in != null) {
                        map.put(rs.getString(1), (Serializable) i.readObject());
                    }
                }
            });
            return map;
        }
    }

    /**
     * Session store with Redis
     */
    public static class SessionStoreRedis implements SessionStore {

        /**
         * Redis client
         */
        final Redis redis = Try.s(() -> new Redis(Sys.session_redis_host, Sys.session_redis_port)).get();

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.AutoCloseable#close()
         */
        @Override
        public void close() throws Exception {
            redis.close();
        }

        @Override
        public void save(String id, Map<String, Serializable> keyValues, Set<String> removeKeys) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            keyValues.forEach(Try.biC((key, value) -> {
                redis.command("HSET", id, key, Tool.serialize(value, out));
            }));
            removeKeys.forEach(Try.c(key -> {
                redis.command("HDEL", id, key);
            }));
        }

        @Override
        public Map<String, Serializable> load(String id) {
            Map<String, Serializable> map = new ConcurrentHashMap<>();
            try {
                redis.command("HGETALL", id);
                for (int i = 0, end = (int) redis.readLong('*'); i + 1 < end; i += 2) {
                    map.put(new String(redis.readBulk(), StandardCharsets.UTF_8), Tool.deserialize(redis.readBulk()));
                }
                redis.command("EXPIRE", id, String.valueOf(Sys.session_timeout_minutes * 60));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return map;
        }
    }

    /**
     * Session implementation
     */
    public static class SessionImpl extends Session {

        /**
         * Session store factory
         */
        @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
        public static Supplier<SessionStore> factory = "redis".equals(Sys.session_store) ? SessionStoreRedis::new : SessionStoreDb::new;

        /**
         * session id
         */
        String id;

        /**
         * Old attributes
         */
        Map<String, Serializable> oldAttributes;

        /**
         * New attributes
         */
        Map<String, Serializable> newAttributes;

        /**
         * Remove attributes
         */
        Set<String> removeAttributes;

        /**
         * @param exchange exchange
         */
        SessionImpl(HttpExchange exchange) {
            id = Tool.of(exchange.getRequestHeaders().getFirst("Cookie"))
                    .flatMap(s -> Stream.of(s.split("\\s*;\\s*")).map(t -> t.split("=", 2)).filter(a -> Sys.session_name.equalsIgnoreCase(a[0])).findAny()
                            .map(a -> a[1].substring(0, a[1].length() - Sys.cluster_suffix.length())))
                    .orElseGet(() -> Tool.peek(Tool.hash("" + hashCode() + System.currentTimeMillis() + exchange.getRemoteAddress() + Math.random()),
                            i -> exchange.getResponseHeaders().add("Set-Cookie", createSetCookie(Sys.session_name, i + Sys.cluster_suffix, null, -1, null,
                                    Application.current().map(Application::getContextPath).orElse(null), false, true))));
        }

        /**
         * @return Non null old attributes
         */
        Map<String, Serializable> oldAttributes() {
            if (oldAttributes == null) {
                try (SessionStore store = factory.get()) {
                    oldAttributes = store.load(id);
                } catch (Exception e) {
                    Log.warning(e, () -> "close error");
                }
            }
            return oldAttributes;
        }

        /**
         * @return Non null new attributes
         */
        Map<String, Serializable> newAttributes() {
            if (newAttributes == null) {
                newAttributes = new ConcurrentHashMap<>();
            }
            return newAttributes;
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
            Tool.of(expires).map(DateTimeFormatter.RFC_1123_DATE_TIME::format).ifPresent(s -> result.append("; Expires=").append(s));
            if (maxAge > 0) {
                result.append("; Max-Age=").append(maxAge);
            }
            Tool.of(domain).ifPresent(s -> result.append("; Domain=").append(s));
            Tool.of(path).ifPresent(s -> result.append("; Path=").append(s));
            if (secure) {
                result.append("; Secure");
            }
            if (httpOnly) {
                result.append("; HttpOnly");
            }
            return result.toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        @SuppressFBWarnings("EQ_UNUSUAL")
        public boolean equals(Object obj) {
            return obj != null && hashCode() == obj.hashCode();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return id == null ? 0 : id.hashCode();
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public Stream<String> names() {
            return Stream.concat(oldAttributes().keySet().stream(), newAttributes().keySet().stream()).distinct()
                    .filter(Tool.of(removeAttributes).map(a -> Tool.not(a::contains)).orElse(i -> true));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> Optional<T> getAttr(String name) {
            return Tool.of(newAttributes().containsKey(name) ? (T) newAttributes().get(name)
                    : oldAttributes().containsKey(name) && !Tool.of(removeAttributes).map(a -> a.contains(name)).orElse(false) ? (T) oldAttributes().get(name)
                            : Reflector.getProperty(this, name, () -> null));
        }

        @Override
        public void setAttr(String name, Serializable value) {
            if (!Objects.equals(value, oldAttributes().get(name))) {
                newAttributes().put(name, value);
                Tool.of(removeAttributes).ifPresent(a -> a.remove(name));
            }
        }

        @Override
        public void removeAttr(String name) {
            if (oldAttributes().containsKey(name)) {
                if (removeAttributes == null) {
                    removeAttributes = new HashSet<>();
                }
                removeAttributes.add(name);
                Tool.of(newAttributes).ifPresent(a -> a.remove(name));
            }
        }

        /**
         * save session attributes
         */
        public void save() {
            boolean hasNew = newAttributes != null && !newAttributes.isEmpty();
            boolean hasRemove = removeAttributes != null && !removeAttributes.isEmpty();
            if (!hasNew && !hasRemove) {
                return;
            }
            try (SessionStore store = factory.get(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                store.save(id, hasNew ? newAttributes : Collections.emptyMap(), hasRemove ? removeAttributes : Collections.emptySet());
                if (hasNew) {
                    newAttributes.forEach(oldAttributes::put);
                    newAttributes = null;
                }
                if (hasRemove) {
                    removeAttributes.forEach(oldAttributes::remove);
                    removeAttributes = null;
                }
            } catch (IOException e) {
                Log.severe(e, () -> "session save error");
            } catch (Exception e) {
                Log.warning(e, () -> "close error");
            }
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
         * Request method
         */
        final Method method;

        /**
         * Accept methods
         */
        static final Set<String> methods = Stream.of(Method.values()).map(Enum::name).collect(Collectors.toSet());

        /**
         * @param exchange exchange
         * @throws IOException IO error
         */
        RequestImpl(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
            String path = exchange.getRequestURI().getPath();
            String contextPath = Application.current().map(Application::getContextPath).orElse("");
            this.path = path.length() <= contextPath.length() ? "/" : Tool.prefix(path.substring(contextPath.length()), "/");

            // query parameter
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                parse(parameters, new Scanner(query));
            }

            // request body
            requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");
            Function<String, Method> getMethod = Try.f(m -> Method.valueOf(m.toUpperCase()), (e, m) -> {
                Log.info(e, () -> "Invalid method: " + m);
                return null;
            });
            Method method = getMethod.apply(Tool.getFirst(parameters, Sys.request_method_key).orElseGet(exchange::getRequestMethod));
            if (contentType == null || method == null) {
                this.method = method;
                return;
            }
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
                                Log.config(pair.r + " deleted " + pair.r.delete());
                            }
                            if (pair.l == null) {
                                Log.info("413 payload too large");
                                break loop;
                            }
                            Tool.addValue(parameters, name, new String(pair.l, StandardCharsets.UTF_8));
                        } else {
                            Tool.addValue(parameters, name, filename);
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
                                    Log.info("saved " + f + " " + length + "bytes");
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
            this.method = getMethod.apply(Tool.getFirst(parameters, Sys.request_method_key).orElseGet(exchange::getRequestMethod));
        }

        @Override
        public Stream<String> names() {
            return attributes.keySet().stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Tool.of(attributes.containsKey(name) ? (T) attributes.get(name) : Reflector.getProperty(this, name, () -> null));
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
            return method;
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
                Log.info("saved " + f + " " + size + "bytes");
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
                    Tool.addValue(parameters, URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()),
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
        void flush() {
            Session.current().filter(s -> s instanceof SessionImpl).ifPresent(s -> ((SessionImpl) s).save());
            super.flush();
        }

        @Override
        public void writeResponse(Consumer<Supplier<OutputStream>> writeBody) {
            HttpExchange exchange = ((RequestImpl) Request.current().get()).exchange;
            TryConsumer<Long> action = contentLength -> {
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
                    action.accept(headers == null ? 0L : Tool.getFirst(headers, "Content-Length").flatMap(Tool::longInteger).orElse(0L));
                    return exchange.getResponseBody();
                }));
            }
            exchange.close();
        }

        @Override
        public String toString() {
            return Request.current().map(i -> ((RequestImpl) i).exchange)
                    .map(request -> "-> " + request.getProtocol() + " "
                            + Status.of(request.getResponseCode()).map(Object::toString).orElseGet(() -> String.valueOf(request.getResponseCode()))
                            + Tool.string(request.getResponseHeaders().getFirst("Content-Type")).map(type -> " (" + type + ")").orElse(""))
                    .orElse("");
        }
    }
}
