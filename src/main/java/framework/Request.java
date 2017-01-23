package framework;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import framework.annotation.Http;

/**
 * request scoped object
 */
public class Request implements Attributes<Object> {

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Request.class.getCanonicalName());

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
        Try.r(() -> raw.setCharacterEncoding(StandardCharsets.UTF_8.name())).run();
        String uri = raw.getRequestURI();
        int rootLength = raw.getContextPath().length() + 1;
        path = rootLength > uri.length() ? null : Tool.string(uri.substring(rootLength)).orElse("index.html");
        logger.info(() -> raw.hashCode() + "<- " + raw.getMethod() + " " + path
                + Tool.string(Stream
                        .concat(Stream.of(Optional.ofNullable(raw.getQueryString()).orElse("").split("&")),
                                Tool.stream(raw.getParameterNames()).map(name -> name + "=" + String.join(",", raw.getParameterValues(name))))
                        .collect(Collectors.joining("&"))).map(s -> '?' + s).orElse(""));
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
}
