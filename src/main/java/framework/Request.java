package framework;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import app.config.Sys;
import framework.annotation.Route;

/**
 * request scoped object
 */
public abstract class Request implements Attributes<Object> {

    /**
     * current request
     */
    transient static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    /**
     * @return current request
     */
    public static Optional<Request> current() {
        return Tool.of(CURRENT.get());
    }

    /**
     * @return path
     */
    public abstract String getPath();

    /**
     * @return Folder name(with end separator)
     */
    public String getFolder() {
        return Tool.getFolder(getPath());
    }

    /**
     * @return file name(without extension)
     */
    public String getName() {
        return Tool.getName(getPath());
    }

    /**
     * @return extension(with period)
     */
    public String getExtension() {
        return Tool.getExtension(getPath());
    }

    /**
     * @return file name(with extension)
     */
    public String getFile() {
        return getName() + getExtension();
    }

    /**
     * @return http method
     */
    public abstract Route.Method getMethod();

    @Override
    public String toString() {
        return "<- " + getMethod() + " " + getPath() + Tool.string(getParameters().entrySet()
            .stream()
            .map(pair -> pair.getKey() + "=" + Tool.cut(pair.getValue()
                .toString(), Sys.Log.parameter_max_letters, " ..."))
            .collect(Collectors.joining("&")))
            .map(s -> '?' + s)
            .orElse("");
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
        context.init(null, new TrustManager[] { new X509TrustManager() {

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
        } }, null);
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
                Log.info(new String(bytes, 0, n, StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * @return files
     */
    public abstract Map<String, Tuple<byte[], File>> getFiles();

    /**
     * @return headers
     */
    public abstract Map<String, List<String>> getHeaders();

    /**
     * @return parameters
     */
    public abstract Map<String, List<String>> getParameters();

    /**
     * @return Remote IP address
     */
    protected abstract String getRemoteAddr();

    /**
     * @return Remote IP address
     */
    public String getRemoteIp() {
        return Tool.val(getHeaders(), map -> Tool.or(Tool.getFirst(map, "X-FORWARDED-FOR")
            .filter(i -> i.length() >= 4 && !"unknown".equalsIgnoreCase(i)), () -> Tool.getFirst(map, "INTEL_SOURCE_IP")
                .filter(i -> !"unknown".equalsIgnoreCase(i)), () -> Tool.getFirst(map, "PROXY-CLIENT-IP")
                    .filter(i -> !"unknown".equalsIgnoreCase(i)), () -> Tool.getFirst(map, "WL-PROXY-CLIENT-IP")
                        .filter(i -> !"unknown".equalsIgnoreCase(i)), () -> Tool.getFirst(map, "HTTP_CLIENT_IP")
                            .filter(i -> !"unknown".equalsIgnoreCase(i)), () -> Tool.getFirst(map, "HTTP_X_FORWARDED_FOR")
                                .filter(i -> !"unknown".equalsIgnoreCase(i)), () -> Tool.of(getRemoteAddr()))
            .orElse("unknwon"));
    }

    /**
     * @return parameters
     */
    public Map<String, String> getFirstParameters() {
        Map<String, List<String>> map = getParameters();
        return new Map<String, String>() {

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return map.containsKey(key);
            }

            @Override
            public boolean containsValue(Object value) {
                return Stream.of(map.values())
                    .anyMatch(i -> Objects.equals(i, value));
            }

            @Override
            public String get(Object key) {
                return Tool.getFirst(map, (String) key)
                    .orElse(null);
            }

            @Override
            public String put(String key, String value) {
                return Tool.setValue(map, key, value);
            }

            @Override
            public String remove(Object key) {
                return map.remove(key)
                    .get(0);
            }

            @Override
            public void putAll(Map<? extends String, ? extends String> m) {
                m.forEach((key, value) -> Tool.setValue(map, key, value));
            }

            @Override
            public void clear() {
                map.clear();
            }

            @Override
            public Set<String> keySet() {
                return map.keySet();
            }

            @Override
            public Collection<String> values() {
                return map.values()
                    .stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            }

            @Override
            public Set<java.util.Map.Entry<String, String>> entrySet() {
                return map.entrySet()
                    .stream()
                    .map(entry -> Tuple.of(entry.getKey(), Tool.of(entry.getValue())
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.get(0))
                        .orElse(null)))
                    .collect(Collectors.toSet());
            }
        };
    }
}
