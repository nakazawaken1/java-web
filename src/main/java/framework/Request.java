package framework;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.annotation.Http;
import framework.annotation.Http.Method;

/**
 * request scoped object
 */
@SuppressWarnings("restriction")
public abstract class Request implements Attributes<Object> {

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Request.class.getCanonicalName());

    /**
     * current request
     */
    transient static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    public static Optional<Request> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * for servlet
     */
    static class ForServlet extends Request {

        /**
         * http request
         */
        final HttpServletRequest servletRequest;

        /**
         * http response
         */
        final HttpServletResponse servletResponse;

        /**
         * request path
         */
        final String path;

        /**
         * constructor
         *
         * @param request servlet request
         * @param response servlet response
         */
        ForServlet(HttpServletRequest request, HttpServletResponse response) {
            Try.r(() -> request.setCharacterEncoding(StandardCharsets.UTF_8.name())).run();
            servletRequest = request;
            servletResponse = response;
            String uri = request.getRequestURI();
            int rootLength = request.getContextPath().length() + 1;
            path = rootLength > uri.length() ? null : Tool.string(uri.substring(rootLength)).orElse("index.html");
        }

        @Override
        public int hashCode() {
            return servletRequest.hashCode();
        }

        /**
         * @return http method
         */
        @Override
        public Http.Method getMethod() {
            return Enum.valueOf(Http.Method.class, servletRequest.getMethod());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#names()
         */
        @Override
        public Stream<String> names() {
            return Tool.stream(servletRequest.getAttributeNames());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#getAttr(java.lang.String)
         */
        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Optional.ofNullable((T) servletRequest.getAttribute(name));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Object value) {
            servletRequest.setAttribute(name, value);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#removeAttr(java.lang.String)
         */
        @Override
        public void removeAttr(String name) {
            servletRequest.removeAttribute(name);
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Map<String, Pair<byte[], File>> getFiles() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return new Attributes<List<String>>() {

                @Override
                public Stream<String> names() {
                    return Tool.stream(servletRequest.getHeaderNames());
                }

                @SuppressWarnings("unchecked")
                @Override
                public <T extends List<String>> Optional<T> getAttr(String name) {
                    return (Optional<T>) Optional.ofNullable(servletRequest.getHeaders(name)).map(e -> Tool.stream(e).collect(Collectors.toList()));
                }

                @Override
                public void setAttr(String name, List<String> value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void removeAttr(String name) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public Map<String, List<String>> getParameters() {
            return new Attributes<List<String>>() {

                @Override
                public Stream<String> names() {
                    return Tool.stream(servletRequest.getParameterNames());
                }

                @SuppressWarnings("unchecked")
                @Override
                public <T extends List<String>> Optional<T> getAttr(String name) {
                    return (Optional<T>) Optional.ofNullable(servletRequest.getParameterValues(name)).map(a -> Arrays.asList(a));
                }

                @Override
                public void setAttr(String name, List<String> value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void removeAttr(String name) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    /**
     * For server
     */
    static class ForServer extends Request {

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
        final Map<String, Pair<byte[], File>> files = new LinkedHashMap<>(); // file name, file content
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
        ForServer(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
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
                        loop:
                        for (;;) {
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
                                Pair<byte[], File> pair = readBody(in, boundary);
                                if (pair.b != null) {
                                    logger.config(pair.b + " deleted " + pair.b.delete());
                                }
                                if (pair.a == null) {
                                    logger.info("413 payload too large");
                                    break loop;
                                }
                                add(parameters, name, new String(pair.a, StandardCharsets.UTF_8));
                            } else {
                                add(parameters, name, filename);
                                if (length > 0) {
                                    if (length < fileSizeThreshold) {
                                        byte[] bytes = new byte[length];
                                        int n = in.read(bytes);
                                        files.put(filename, Tool.pair(Arrays.copyOfRange(bytes, 0, n), null));
                                    } else {
                                        File f = File.createTempFile("upload", "file");
                                        f.deleteOnExit();
                                        try (FileOutputStream out = new FileOutputStream(f); FileChannel to = out.getChannel()) {
                                            to.transferFrom(Channels.newChannel(in), 0, length);
                                        }
                                        logger.info("saved " + f + " " + length + "bytes");
                                        files.put(filename, Tool.pair(null, f));
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
            return Optional.ofNullable((T) attributes.get(name));
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
        public Map<String, Pair<byte[], File>> getFiles() {
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
        static Pair<byte[], File> readBody(InputStream in, String boundary) throws IOException {
            ByteArrayOutputStream lines = new ByteArrayOutputStream();
            OutputStream out = lines;
            try {
                long size = 0;
                boolean crlf = false;
                File f = null;
                for (;;) {
                    if (f == null && size >= fileSizeThreshold) {
                        f = File.createTempFile("upload", "file");
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
                    return Tool.pair(lines.toByteArray(), null);
                }
                logger.info("saved " + f + " " + size + "bytes");
                return Tool.pair(null, f);
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
     * @return path
     */
    public abstract String getPath();

    /**
     * @return http method
     */
    public abstract Http.Method getMethod();

    @Override
    public String toString() {
        return "<- " + getMethod() + " " + getPath() + Tool.string(getParameters().entrySet().stream().map(pair -> pair.getKey() + "=" + pair.getValue()).collect(Collectors.joining("&"))).map(s -> '?' + s).orElse("");
    }

    /**
     * @param args URL(https)
     * @throws NoSuchAlgorithmException algorithm error
     * @throws KeyManagementException key error
     * @throws IOException IO error
     * @throws MalformedURLException url error
     */
    public static void main(String[] args) throws NoSuchAlgorithmException, KeyManagementException, MalformedURLException, IOException {
        String url = args.length > 0 ? args[0] : "https://localhost:8443";
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new X509TrustManager() {

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }}, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = new byte[256];
            for (;;) {
                int n = in.read(bytes);
                if (n <= 0) {
                    break;
                }
                Logger.getGlobal().info(new String(bytes, 0, n, StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * @return files
     */
    public abstract Map<String, Pair<byte[], File>> getFiles();

    /**
     * @return headers
     */
    public abstract Map<String, List<String>> getHeaders();

    /**
     * @return parameters
     */
    public abstract Map<String, List<String>> getParameters();

    /**
     * @param name parameter name
     * @return first value
     */
    public String getFirstParameter(String name) {
        return Optional.ofNullable(getParameters().get(name)).filter(a -> !a.isEmpty()).map(a -> a.get(0)).orElse(null);
    }
}
