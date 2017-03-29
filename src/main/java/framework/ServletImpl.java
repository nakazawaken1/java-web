package framework;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.annotation.Route;

/**
 * Servlet implementation
 */
@WebServlet("/")
public class ServletImpl implements javax.servlet.Servlet {

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
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
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        try (Defer<Request> request = new Defer<>(new RequestImpl((HttpServletRequest) req, (HttpServletResponse) res), r -> Request.CURRENT.remove());
                Defer<Lazy<Session>> session = new Defer<>(new Lazy<>(() -> {
                    Session s = new SessionImpl(((HttpServletRequest) req).getSession());
                    Session.CURRENT.set(s);
                    return s;
                }), s -> s.ifGot(i -> Session.CURRENT.remove()).close())) {
            Request.CURRENT.set(request.get());
            Application.current().get().handle(request.get(), session.get());
        }
    }

    /**
     * Application implementation
     */
    static class ApplicationImpl extends Application {
        @Override
        public String toString() {
            return "real path: " + raw.getRealPath("") + ", context path: " + raw.getContextPath();

        }

        /**
         * application scope object
         */
        transient final ServletContext raw;

        /**
         * constructor
         * 
         * @param raw application scope object
         */
        @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
        ApplicationImpl(ServletContext raw) {
            this.raw = raw;
            CURRENT = this;
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
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> raw.getAttribute(name)));
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
         * @param relativePath relative path from htdocs
         * @return full path
         */
        @Override
        public String toRealPath(String relativePath) {
            return raw.getRealPath(relativePath);
        }

        /**
         * @return context path
         */
        @Override
        public String getContextPath() {
            return Tool.suffix(raw.getContextPath(), "/");
        }
    }

    /**
     * Session implementation
     */
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
            session.setMaxInactiveInterval(Config.app_session_timeout_minutes.integer() * 60);
        }

        /*
         * (non-Javadoc)
         * 
         * @see framework.Attributes#clear()
         */
        @Override
        public void clear() {
            session.invalidate();
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
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> session.getAttribute(name)));
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
    static class RequestImpl extends Request {

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
        RequestImpl(HttpServletRequest request, HttpServletResponse response) {
            Try.r(() -> request.setCharacterEncoding(StandardCharsets.UTF_8.name())).run();
            servletRequest = request;
            servletResponse = response;
            String uri = request.getRequestURI();
            int rootLength = request.getContextPath().length() + 1;
            path = rootLength > uri.length() ? null : Tool.string(uri.substring(rootLength)).orElse("index.html");
        }

        /**
         * @return request id
         */
        public int getId() {
            return servletRequest.hashCode();
        }

        /**
         * @return http method
         */
        @Override
        public Route.Method getMethod() {
            return Enum.valueOf(Route.Method.class, servletRequest.getMethod());
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
            return Optional.ofNullable((T) getters.get(this, name).orElseGet(() -> servletRequest.getAttribute(name)));
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
        public Map<String, Tuple<byte[], File>> getFiles() {
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
     * Response implementation
     */
    static class ResponseImpl extends Response {

        @Override
        public void writeResponse(Consumer<Supplier<OutputStream>> writeBody) {
            HttpServletResponse response = ((RequestImpl) Request.current().get()).servletResponse;
            Runnable action = () -> {
                response.setCharacterEncoding(charset.name());
                Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> response.setHeader(i[0], i[1]));
                if (headers != null) {
                    headers.forEach((key, values) -> values.forEach(value -> response.addHeader(key, value)));
                }
                response.setStatus(status);
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
            return Request.current()
                    .map(i -> i.getId() + Optional.of(((RequestImpl) i).servletResponse).map(r -> "-> " + r.getStatus() + " " + r.getContentType()).orElse(""))
                    .orElse("");
        }
    }
}
