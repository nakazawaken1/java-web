package framework;

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
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import framework.annotation.Http;

/**
 * request scoped object
 */
public class Request implements Attributes<Object> {

    @Override
    public String toString() {
        return raw.hashCode() + "<- " + raw.getMethod() + " " + path
                + Tool.string(Stream
                        .concat(Stream.of(Optional.ofNullable(raw.getQueryString()).orElse("").split("&")),
                                Tool.stream(raw.getParameterNames()).map(name -> name + "=" + String.join(",", raw.getParameterValues(name))))
                        .collect(Collectors.joining("&"))).map(s -> '?' + s).orElse("");
    }

    /**
     * http request
     */
    transient static final ThreadLocal<HttpServletRequest> request = new ThreadLocal<>();

    /**
     * http response
     */
    transient static final ThreadLocal<HttpServletResponse> response = new ThreadLocal<>();

    /**
     * current request
     */
    transient final HttpServletRequest raw = request.get();

    /**
     * request path
     */
    public final String path;

    /**
     * constructor
     */
    Request() {
        String uri = raw.getRequestURI();
        int rootLength = raw.getContextPath().length() + 1;
        path = rootLength > uri.length() ? null : Tool.string(uri.substring(rootLength)).orElse("index.html");
    }

    /**
     * @return http method
     */
    public Http.Method getMethod() {
        return Enum.valueOf(Http.Method.class, raw.getMethod());
    }

    /*
     * (non-Javadoc)
     * 
     * @see framework.Attributes#names()
     */
    @Override
    public Stream<String> names() {
        return Tool.stream(raw.getAttributeNames());
    }

    /*
     * (non-Javadoc)
     * 
     * @see framework.Attributes#getAttr(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getAttr(String name) {
        return Optional.ofNullable((T) raw.getAttribute(name));
    }

    /*
     * (non-Javadoc)
     * 
     * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
     */
    @Override
    public void setAttr(String name, Object value) {
        raw.setAttribute(name, value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see framework.Attributes#removeAttr(java.lang.String)
     */
    @Override
    public void removeAttr(String name) {
        raw.removeAttribute(name);
    }

    /**
     * @param args URL(https)
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws MalformedURLException
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
                Logger.getGlobal().info(new String(bytes, 0, n, StandardCharsets.UTF_8));
            }
        }
    }
}
