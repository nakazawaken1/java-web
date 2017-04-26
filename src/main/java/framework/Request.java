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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
     * getters
     */
    static final Getters getters = new Getters(Request.class);

    /**
     * @return current request
     */
    public static Optional<Request> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * @return request id
     */
    public int getId() {
        return hashCode();
    }

    /**
     * @return path
     */
    public abstract String getPath();
    
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
     * @return http method
     */
    public abstract Route.Method getMethod();

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
}
