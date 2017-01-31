package framework;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import com.sun.net.httpserver.HttpExchange;

import framework.Try.TryConsumer;
import framework.Try.TryTriConsumer;

/**
 * Response
 */
@SuppressWarnings("restriction")
public abstract class Response {

    /**
     * creator
     */
    static Supplier<ResponseCreator> create;

    /**
     * logger
     */
    transient private static final Logger logger = Logger.getLogger(Response.class.getCanonicalName());

    /**
     * creator
     */
    static interface ResponseCreator {

        /**
         * @param file file
         * @param values values
         * @return response
         */
        Response ofFile(String file, Object... values);

        /**
         * @param name part name
         * @param replacer replacer(writer, expression, prefix)
         * @return response
         */
        Response ofWriteTemplate(String name, TryTriConsumer<PrintWriter, String, String> replacer);

        /**
         * @param name part name
         * @param values values
         * @return response
         */
        Response ofTemplate(String name, Object... values);

        /**
         * @param out output with output stream
         * @return response
         */
        Response ofOut(Consumer<OutputStream> out);

        /**
         * @param write output with print writer
         * @return response
         */
        Response ofWrite(Consumer<PrintWriter> write);

        /**
         * redirect
         * 
         * @param location location
         * @param status status code
         * @return response
         */
        Response ofRedirect(String location, int status);

        /**
         * @param o object
         * @return response
         */
        Response ofText(Object o);

        /**
         * @param o object
         * @return response
         */
        Response ofJson(Object o);

        /**
         * @param status HTTP status code
         * @return response
         */
        Response ofError(int status);
    }

    /**
     * For servlet
     */
    static class ForServlet extends Response implements ResponseCreator {

        /**
         * constructor
         * 
         * @param consumer response action
         * @return self
         */
        ForServlet set(TryConsumer<HttpServletResponse> consumer) {
            this.consumer = consumer;
            return this;
        }

        /**
         * response consumer
         */
        TryConsumer<HttpServletResponse> consumer;

        @Override
        public Response ofFile(String file, Object... values) {
            return new ForServlet().set(r -> {
                r.setContentType(Tool.getContentType(file));
                Path path = Paths.get(Config.toURL(Config.app_view_folder.text(), file).get().toURI());
                if (Config.app_format_include_regex.stream().anyMatch(file::matches) && Config.app_format_exclude_regex.stream().noneMatch(file::matches)) {
                    try (Stream<String> lines = Files.lines(path); PrintWriter writer = r.getWriter()) {
                        Function<Formatter, Formatter.Result> exclude;
                        Function<Object, String> escape;
                        if (file.endsWith(".js")) {
                            exclude = Formatter::excludeForScript;
                            escape = Formatter::scriptEscape;
                        } else if (file.endsWith(".css")) {
                            exclude = Formatter::excludeForStyle;
                            escape = null;
                        } else {
                            exclude = Formatter::excludeForHtml;
                            escape = Formatter::htmlEscape;
                        }
                        try (Formatter formatter = new Formatter(exclude, escape, map, values)) {
                            lines.forEach(line -> writer.println(formatter.format(line)));
                        }
                    }
                } else {
                    r.getOutputStream().write(Files.readAllBytes(path));
                }
            });
        }

        @Override
        public Response ofWriteTemplate(String name, TryTriConsumer<PrintWriter, String, String> replacer) {
            return new ForServlet().set(r -> {
                r.setContentType("text/html;charset=" + charset);
                try (PrintWriter writer = r.getWriter();
                        Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).get().toURI()));
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, null)) {
                    lines.map(formatter::format).forEach(line -> {
                        Tool.printFormat(writer, line, replacer, "#{", "}", "${", "}", "<!--{", "}-->", "/*{", "}*/", "{/*", "*/}");
                        writer.println();
                    });
                }
            });
        }

        @Override
        public Response ofTemplate(String name, Object... values) {
            return new ForServlet().set(r -> {
                r.setContentType("text/html;charset=" + charset);
                try (PrintWriter writer = r.getWriter();
                        Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).get().toURI()));
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, map, values)) {
                    lines.forEach(line -> writer.println(formatter.format(line)));
                }
            });
        }

        @Override
        public Response ofOut(Consumer<OutputStream> out) {
            return new ForServlet().set(r -> {
                out.accept(r.getOutputStream());
            });
        }

        @Override
        public Response ofWrite(Consumer<PrintWriter> write) {
            return new ForServlet().set(r -> write.accept(r.getWriter()));
        }

        @Override
        public Response ofRedirect(String location, int status) {
            return new ForServlet().set(r -> {
                r.setStatus(status);
                r.setHeader("Location", location);
            });
        }

        @Override
        public Response ofText(Object o) {
            return new ForServlet().set(r -> {
                r.setContentType("text/plain;charset=" + charset);
                r.getWriter().print(o);
            });
        }

        @Override
        public Response ofJson(Object o) {
            return new ForServlet().set(r -> {
                r.setContentType("application/json;charset=" + charset);
                r.getWriter().print(Tool.json(o));
            });
        }

        @Override
        public Response ofError(int status) {
            return new ForServlet().set(r -> {
                r.setStatus(status);
            });
        }

        @Override
        public void flush() {
            HttpServletResponse response = ((Request.ForServlet) Request.current.get()).servletResponse;
            response.setCharacterEncoding(charset.name());
            Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> response.setHeader(i[0], i[1]));
            Try.c(consumer).accept(response);
            super.flush();
        }

        @Override
        public String toString() {
            return Request.current.get().hashCode() + Optional.of(((Request.ForServlet) Request.current.get()).servletResponse)
                    .map(r -> "-> " + r.getStatus() + " " + r.getContentType()).orElse("");
        }
    }

    /**
     * For server
     */
    static class ForServer extends Response implements ResponseCreator {

        /**
         * constructor
         * 
         * @param consumer response action
         * @return self
         */
        ForServer set(TryConsumer<HttpExchange> consumer) {
            this.consumer = consumer;
            return this;
        }

        /**
         * response consumer
         */
        TryConsumer<HttpExchange> consumer;

        @Override
        public Response ofFile(String file, Object... values) {
            return new ForServer().set(r -> {
                r.getResponseHeaders().set("Content-Type", Tool.getContentType(file));
                r.sendResponseHeaders(200, 0);
                Path path = Paths.get(Config.toURL(Config.app_view_folder.text(), file).get().toURI());
                if (Config.app_format_include_regex.stream().anyMatch(file::matches) && Config.app_format_exclude_regex.stream().noneMatch(file::matches)) {
                    try (Stream<String> lines = Files.lines(path); OutputStream out = r.getResponseBody()) {
                        Function<Formatter, Formatter.Result> exclude;
                        Function<Object, String> escape;
                        if (file.endsWith(".js")) {
                            exclude = Formatter::excludeForScript;
                            escape = Formatter::scriptEscape;
                        } else if (file.endsWith(".css")) {
                            exclude = Formatter::excludeForStyle;
                            escape = null;
                        } else {
                            exclude = Formatter::excludeForHtml;
                            escape = Formatter::htmlEscape;
                        }
                        try (Formatter formatter = new Formatter(exclude, escape, map, values)) {
                            lines.forEach(Try.c(line -> out.write(formatter.format(line + Tool.CRLF).getBytes(charset))));
                        }
                    }
                } else {
                    r.getResponseBody().write(Files.readAllBytes(path));
                }
            });
        }

        @Override
        public Response ofWriteTemplate(String name, TryTriConsumer<PrintWriter, String, String> replacer) {
            return new ForServer().set(r -> {
                r.getResponseHeaders().set("Content-Type", "text/html;charset=" + charset);
                r.sendResponseHeaders(200, 0);
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(r.getResponseBody()));
                        Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).get().toURI()));
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, null)) {
                    lines.map(formatter::format).forEach(line -> {
                        Tool.printFormat(writer, line, replacer, "#{", "}", "${", "}", "<!--{", "}-->", "/*{", "}*/", "{/*", "*/}");
                        writer.println();
                    });
                }
            });
        }

        @Override
        public Response ofTemplate(String name, Object... values) {
            return new ForServer().set(r -> {
                r.getResponseHeaders().set("Content-Type", "text/html;charset=" + charset);
                r.sendResponseHeaders(200, 0);
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(r.getResponseBody()));
                        Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).get().toURI()));
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, map, values)) {
                    lines.forEach(line -> writer.println(formatter.format(line)));
                }
            });
        }

        @Override
        public Response ofOut(Consumer<OutputStream> out) {
            return new ForServer().set(r -> {
                r.sendResponseHeaders(200, 0);
                out.accept(r.getResponseBody());
            });
        }

        @Override
        public Response ofWrite(Consumer<PrintWriter> write) {
            return new ForServer().set(r -> {
                r.sendResponseHeaders(200, 0);
                write.accept(new PrintWriter(new OutputStreamWriter(r.getResponseBody())));
            });
        }

        @Override
        public Response ofRedirect(String location, int status) {
            return new ForServer().set(r -> {
                r.getResponseHeaders().set("Location", location);
                r.sendResponseHeaders(status, -1);
            });
        }

        @Override
        public Response ofText(Object o) {
            return new ForServer().set(r -> {
                r.getResponseHeaders().set("Content-Type", "text/plain;charset=" + charset);
                r.sendResponseHeaders(200, 0);
                r.getResponseBody().write(o.toString().getBytes(charset));
            });
        }

        @Override
        public Response ofJson(Object o) {
            return new ForServer().set(r -> {
                r.getResponseHeaders().set("Content-Type", "application/json;charset=" + charset);
                r.getResponseBody().write(Tool.json(o).getBytes(charset));
            });
        }

        @Override
        public Response ofError(int status) {
            return new ForServer().set(r -> {
                r.sendResponseHeaders(status, -1);
            });
        }

        @Override
        public void flush() {
            HttpExchange exchange = ((Request.ForServer) Request.current.get()).exchange;
            Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> exchange.getResponseHeaders().set(i[0], i[1]));
            Try.c(consumer).accept(exchange);
            super.flush();
        }

        @Override
        public String toString() {
            return Request.current.get().hashCode() + Optional.of(((Request.ForServer) Request.current.get()).exchange)
                    .map(r -> "-> " + r.getResponseCode() + " " + r.getResponseHeaders().get("Content-Type")).orElse("");
        }
    }

    /**
     * encoding
     */
    static Charset charset = StandardCharsets.UTF_8;

    /**
     * 属性
     */
    Map<String, Object> map;

    /**
     * @param key key
     * @param value value
     * @return self
     */
    public Response bind(String key, Object value) {
        if (map == null) {
            map = new HashMap<>();
        }
        map.put(key, value);
        return this;
    }

    /**
     * @param map map
     * @return self
     */
    public Response bind(Map<String, Object> map) {
        if (this.map == null) {
            this.map = map;
        } else {
            this.map.putAll(map);
        }
        return this;
    }

    /**
     * @param file file
     * @param values values
     * @return response
     */
    public static Response file(String file, Object... values) {
        Objects.requireNonNull(file);
        Map<String, Object> map = new HashMap<>();
        return create.get().ofFile(file, values).bind(map);
    }

    /**
     * @param name part name
     * @param replacer replacer(writer, expression, prefix)
     * @return response
     */
    public static Response writeTemplate(String name, TryTriConsumer<PrintWriter, String, String> replacer) {
        Objects.requireNonNull(name);
        return create.get().ofWriteTemplate(name, replacer);
    }

    /**
     * @param name part name
     * @param values values
     * @return response
     */
    public static Response template(String name, Object... values) {
        Objects.requireNonNull(name);
        Map<String, Object> map = new HashMap<>();
        return create.get().ofTemplate(name, values).bind(map);
    }

    /**
     * @param out output with output stream
     * @return response
     */
    public static Response out(Consumer<OutputStream> out) {
        Objects.requireNonNull(out);
        return create.get().ofOut(out);
    }

    /**
     * @param write output with print writer
     * @return response
     */
    public static Response write(Consumer<PrintWriter> write) {
        Objects.requireNonNull(write);
        return create.get().ofWrite(write);
    }

    /**
     * 302
     * 
     * @param location location
     * @return response
     */
    public static Response redirect(String location) {
        return redirect(location, 302);
    }

    /**
     * redirect
     * 
     * @param location location
     * @param status status code
     * @return response
     */
    public static Response redirect(String location, int status) {
        Objects.requireNonNull(location);
        return create.get().ofRedirect(location, status);
    }

    /**
     * @param o object
     * @return response
     */
    public static Response text(Object o) {
        return create.get().ofText(o);
    }

    /**
     * @param o object
     * @return response
     */
    public static Response json(Object o) {
        return create.get().ofJson(o);
    }

    /**
     * @param status HTTP status code
     * @return response
     */
    public static Response error(int status) {
        return create.get().ofJson(status);
    }

    /**
     * write response
     */
    void flush() {
        logger.info(toString());
    }
}
