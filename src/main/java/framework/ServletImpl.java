package framework;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import app.config.Sys;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.annotation.Config;
import framework.annotation.Route;
import framework.annotation.Route.Method;

/**
 * Servlet implementation
 */
@WebServlet("/")
@MultipartConfig
public class ServletImpl implements javax.servlet.Servlet {

    /**
     * first access
     */
    AtomicBoolean first = new AtomicBoolean(true);

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        Config.Injector.setup(Sys.class.getPackage().getName());
        Sys.context_path = Tool.suffix(config.getServletContext().getContextPath(), "/");
        new ApplicationImpl(config.getServletContext()).setup(ResponseImpl::new);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#destroy()
     */
    @Override
    public void destroy() {
        Application.current().ifPresent(Application::shutdown);
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
    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        if (first.compareAndSet(true, false)) {
            Sys.http_port = Tool.of(req.getServerPort());
        }
        try (Defer<RequestImpl> request = new Defer<>(Tool.peek(new RequestImpl((HttpServletRequest) req, (HttpServletResponse) res), Request.CURRENT::set),
                r -> Request.CURRENT.remove());
                Defer<SessionImpl> session = new Defer<>(Tool.peek(new SessionImpl(((HttpServletRequest) req).getSession()), Session.CURRENT::set),
                        s -> Session.CURRENT.remove())) {
            Application.current().get().handle(request.get(), session.get());
        }
    }

    /**
     * Application implementation
     */
    @SuppressWarnings("serial")
    static class ApplicationImpl extends Application {

        /**
         * application scope object
         */
        transient final ServletContext context;

        /**
         * constructor
         * 
         * @param context application scope object
         */
        @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
        ApplicationImpl(ServletContext context) {
            this.context = context;
            CURRENT = this;
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#names()
         */
        @Override
        public Stream<String> names() {
            return Tool.stream(context.getAttributeNames());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#getAttr(java.lang.String)
         */
        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Tool.of(Reflector.getProperty(this, name, () -> (T) context.getAttribute(name), false));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Object value) {
            context.setAttribute(name, value);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#removeAttr(java.lang.String)
         */
        @Override
        public void removeAttr(String name) {
            context.removeAttribute(name);
        }

        /**
         * @return context path
         */
        @Override
        public String getContextPath() {
            return Tool.suffix(context.getContextPath(), "/");
        }
    }

    /**
     * Session implementation
     */
    @SuppressWarnings("serial")
    static class SessionImpl extends Session {

        /**
         * http session
         */
        HttpSession session;

        /**
         * constructor
         *
         * @param session session
         */
        SessionImpl(HttpSession session) {
            this.session = session;
            session.setMaxInactiveInterval(Sys.session_timeout_minutes * 60);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return session.getId().hashCode();
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
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return session.getId();
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#clear()
         */
        @Override
        public void clear() {
            Tool.stream(session.getAttributeNames()).forEach(session::removeAttribute);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#names()
         */
        @Override
        public Stream<String> names() {
            return Tool.stream(session.getAttributeNames());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#getAttr(java.lang.String)
         */
        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> Optional<T> getAttr(String name) {
            return Tool.of(Reflector.getProperty(this, name, () -> (T) session.getAttribute(name), false));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Serializable value) {
            session.setAttribute(name, value);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#removeAttr(java.lang.String)
         */
        @Override
        public void removeAttr(String name) {
            session.removeAttribute(name);
        }
    }

    /**
     * Request implementation
     */
    @SuppressWarnings("serial")
    static class RequestImpl extends Request {

        /**
         * http request
         */
        final HttpServletRequest request;

        /**
         * http response
         */
        final HttpServletResponse response;

        /**
         * parameters
         */
        final Map<String, List<String>> parameters;

        /**
         * parameters
         */
        final Map<String, Tuple<byte[], File>> files;

        /**
         * request path
         */
        final String path;

        /**
         * Request method
         */
        final Method method;

        /**
         * constructor
         *
         * @param request servlet request
         * @param response servlet response
         */
        RequestImpl(HttpServletRequest request, HttpServletResponse response) {
            Try.r(() -> request.setCharacterEncoding(StandardCharsets.UTF_8.name())).run();
            this.request = request;
            this.response = response;
            String uri = request.getRequestURI();
            int rootLength = request.getContextPath().length() + 1;
            path = rootLength > uri.length() ? null : Tool.prefix(Tool.string(uri.substring(rootLength)).orElse("/"), "/");
            method = Try.<String, Method> f(m -> Method.valueOf(m.toUpperCase()), (e, m) -> {
                Log.info(e, () -> "Invalid method: " + m);
                return null;
            }).apply(Tool.of(request.getParameter(Sys.request_method_key)).orElseGet(request::getMethod));
            parameters = request.getParameterMap().entrySet().stream().collect(LinkedHashMap::new,
                    (m, e) -> m.put(e.getKey(), new ArrayList<>(Arrays.asList(e.getValue()))), Map::putAll);
            files = new LinkedHashMap<>();
            if (Tool.of(request.getHeader("Content-Type")).filter(i -> i.startsWith("multipart/")).isPresent()) {
                Try.s(request::getParts).get().forEach(part -> {
                    List<String> values = parameters.computeIfAbsent(part.getName(), k -> new ArrayList<>());
                    Optional<String> filename = Tool.of(part.getHeader("Content-Disposition"))
                            .flatMap(s -> Stream.of(s.split("\\s*;\\s*")).filter(i -> i.toLowerCase().startsWith("filename")).findFirst()
                                    .map(i -> i.substring(i.indexOf('=') + 1).trim().replace("\"", "")).filter(i -> !i.isEmpty()));
                    Tool.ifPresentOr(filename, i -> {
                        values.add(i);
                        files.put(i, Tuple.of(Tool.using(part::getInputStream, Tool::loadBytes), null));
                    }, () -> values.add(Tool.using(part::getInputStream, Tool::loadText)));
                });
            }
        }

        /**
         * @return http method
         */
        @Override
        public Route.Method getMethod() {
            return Enum.valueOf(Route.Method.class, request.getMethod());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#names()
         */
        @Override
        public Stream<String> names() {
            return Tool.stream(request.getAttributeNames());
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#getAttr(java.lang.String)
         */
        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getAttr(String name) {
            return Tool.of(Reflector.getProperty(this, name, () -> (T) request.getAttribute(name), false));
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#setAttr(java.lang.String, java.lang.Object)
         */
        @Override
        public void setAttr(String name, Object value) {
            request.setAttribute(name, value);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#removeAttr(java.lang.String)
         */
        @Override
        public void removeAttr(String name) {
            request.removeAttribute(name);
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Map<String, Tuple<byte[], File>> getFiles() {
            return files;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return new Attributes<List<String>>() {

                @Override
                public Stream<String> names() {
                    return Tool.stream(request.getHeaderNames());
                }

                @SuppressWarnings("unchecked")
                @Override
                public <T extends List<String>> Optional<T> getAttr(String name) {
                    return (Optional<T>) Tool.of(request.getHeaders(name)).map(e -> Tool.stream(e).collect(Collectors.toList()));
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
            return parameters;
        }

        @Override
        protected String getRemoteAddr() {
            return request.getRemoteAddr() + ":" + request.getRemotePort();
        }

        @Override
        public String getQuery() {
            return request.getQueryString();
        }
    }

    /**
     * Response implementation
     */
    static class ResponseImpl extends Response {

        @Override
        public void writeResponse(Consumer<Supplier<OutputStream>> writeBody) {
            HttpServletResponse response = ((RequestImpl) Request.current().get()).response;
            Runnable action = () -> {
                headers.forEach((key, values) -> values.forEach(value -> response.addHeader(key, value)));
                charset.ifPresent(c -> response.setCharacterEncoding(c.name()));
                response.setStatus(status.code);
            };
            if (content == null) {
                action.run();
            } else {
                writeBody.accept(Try.s(() -> {
                    action.run();
                    return response.getOutputStream();
                }));
            }
        }

        @Override
        public String toString() {
            return Request.current().map(i -> (RequestImpl) i)
                    .map(i -> "-> " + i.request.getProtocol() + " "
                            + Status.of(i.response.getStatus()).map(Object::toString).orElseGet(() -> String.valueOf(i.response.getStatus()))
                            + Tool.string(i.response.getContentType()).map(type -> " (" + type + ")").orElse(""))
                    .orElse("");
        }
    }
}
