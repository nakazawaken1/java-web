package framework;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import com.sun.net.httpserver.HttpExchange;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Try.TryBiConsumer;
import framework.Try.TryRunnable;
import framework.Try.TryTriConsumer;

/**
 * Response
 */
@SuppressWarnings("restriction")
@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
public abstract class Response {

    /**
     * creator
     */
    static Supplier<Response> create;

    /**
     * logger
     */
    transient final Logger logger = Tool.getLogger();

    /**
     * For servlet
     */
    static class ForServlet extends Response {

        @Override
        public void writeResponse(Consumer<Supplier<OutputStream>> writeBody) {
            HttpServletResponse response = ((Request.ForServlet) Request.current().get()).servletResponse;
            response.setCharacterEncoding(charset.name());
            Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> response.setHeader(i[0], i[1]));
            if (headers != null) {
                headers.forEach((key, values) -> values.forEach(value -> response.addHeader(key, value)));
            }
            Tool.string(response.getContentType()).ifPresent(contentType -> response.setContentType(contentType + ";charset=" + charset));
            response.setStatus(status);
            if (content != null) {
                writeBody.accept(Try.s(response::getOutputStream));
            }
        }

        @Override
        public String toString() {
            return Request.current()
                    .map(i -> i.getId()
                            + Optional.of(((Request.ForServlet) i).servletResponse).map(r -> "-> " + r.getStatus() + " " + r.getContentType()).orElse(""))
                    .orElse("");
        }
    }

    /**
     * For server
     */
    static class ForServer extends Response {

        @Override
        public void writeResponse(Consumer<Supplier<OutputStream>> writeBody) {
            HttpExchange exchange = ((Request.ForServer) Request.current().get()).exchange;
            TryRunnable action = () -> {
                Session.current().map(s -> (Session.ForServer) s).ifPresent(Session.ForServer::save);
                Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> exchange.getResponseHeaders().set(i[0], i[1]));
                if (headers != null) {
                    headers.forEach((key, values) -> values.forEach(value -> exchange.getResponseHeaders().add(key, value)));
                }
                exchange.sendResponseHeaders(status, 0L);
            };
            if (content == null) {
                Try.r(action).run();
            } else {
                writeBody.accept(Try.s(() -> {
                    action.run();
                    return exchange.getResponseBody();
                }));
            }
            exchange.close();
        }

        @Override
        public String toString() {
            return Request.current().map(i -> ((Request.ForServer) i).exchange)
                    .map(r -> "-> " + r.getResponseCode() + " " + r.getResponseHeaders().get("Content-Type")).orElse("");
        }
    }

    /**
     * encoding
     */
    static Charset charset = StandardCharsets.UTF_8;

    /**
     * attributes
     */
    Map<String, Object> map;

    /**
     * arguments
     */
    List<Object> values;

    /**
     * headers
     */
    Map<String, List<String>> headers;

    /**
     * http status code
     */
    int status;

    /**
     * content
     */
    Object content;

    /**
     * @param content content
     * @return response
     */
    public static Response of(Object content) {
        return Tool.peek(create.get(), r -> r.content = content);
    }

    /**
     * @param path location
     * @param status status code
     * @return response
     */
    public static Response redirect(String path, int status) {
        return create.get().status(status).addHeader("Location", path);
    }

    /**
     * @param path location
     * @return response
     */
    public static Response redirect(String path) {
        return redirect(path, 302);
    }

    /**
     * @param status status code
     * @return response
     */
    public static Response error(int status) {
        return create.get().status(status);
    }

    /**
     * @param output output
     * @return response
     */
    public static Response out(Output output) {
        return of(output);
    }

    /**
     * @param writer writer
     * @return response
     */
    public static Response write(Writer writer) {
        return of(writer);
    }

    /**
     * @param file file
     * @return response
     */
    public static Response file(String file) {
        return of(Paths.get(file));
    }

    /**
     * @param status status
     * @return status
     */
    public Response status(int status) {
        this.status = status;
        return this;
    }

    /**
     * @param value value
     * @return self
     */
    public Response bind(Object value) {
        if (values == null) {
            values = new ArrayList<>();
        }
        values.add(value);
        return this;
    }

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
     * @param name name
     * @param value value
     * @return self
     */
    public Response addHeader(String name, String value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        List<String> list = headers.get(name);
        if (list == null) {
            list = new ArrayList<>();
            headers.put(name, list);
        }
        list.add(value);
        return this;
    }

    /**
     * @param name name
     * @param value value
     * @return self
     */
    public Response setHeader(String name, String value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        List<String> list = new ArrayList<>();
        list.add(value);
        headers.put(name, list);
        return this;
    }

    /**
     * @param contentType content type
     * @return self
     */
    public Response contentType(String contentType) {
        return setHeader("Content-Type", contentType);
    }

    /**
     * write response
     */
    void flush() {
        logger.info(toString());
        writeResponse(Try.c(out -> {
            for (Tuple<Class<?>, TryBiConsumer<Response, Supplier<OutputStream>>> pair : writers) {
                if (pair.l.isAssignableFrom(content.getClass())) {
                    pair.r.accept(this, out);
                    break;
                }
            }
        }));
    }

    /**
     * @param writeBody OutputStream
     */
    protected abstract void writeResponse(Consumer<Supplier<OutputStream>> writeBody);

    /**
     * template
     */
    public static class Template {
        /**
         * template name
         */
        String name;
        /**
         * replacer
         */
        TryTriConsumer<PrintWriter, String, String> replacer;

        /**
         * @param name name
         * @param replacer replacer
         */
        public Template(String name, TryTriConsumer<PrintWriter, String, String> replacer) {
            this.name = name;
            this.replacer = replacer;
        }
    }

    /**
     * writer
     */
    @FunctionalInterface
    public interface Writer {
        /**
         * @param writer writer
         */
        void write(PrintWriter writer);
    }

    /**
     * output
     */
    @FunctionalInterface
    public interface Output {
        /**
         * @param out OputputStream
         */
        void output(OutputStream out);
    }

    /**
     * body writer
     */
    static final List<Tuple<Class<?>, TryBiConsumer<Response, Supplier<OutputStream>>>> writers = Arrays
            .asList(Tuple.of(String.class, (r, out) -> out.get().write(((String) r.content).getBytes(charset))), Tuple.of(Writer.class, (r, out) -> {
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get()))) {
                    ((Writer) r.content).write(writer);
                }
            }), Tuple.of(Output.class, (r, out) -> ((Output) r.content).output(out.get())), Tuple.of(Path.class, (r, out) -> {
                Path path = (Path) r.content;
                String file = path.toString();
                r.contentType(Tool.getContentType(file));
                InputStream in = (file.startsWith(File.separator) ? Config.toURL(file) : Config.toURL(Config.app_view_folder.text(), file)).get().openStream();
                if (Config.app_format_include_regex.stream().anyMatch(file::matches) && Config.app_format_exclude_regex.stream().noneMatch(file::matches)) {
                    try (Stream<String> lines = Tool.lines(in); PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get()))) {
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
                        try (Formatter formatter = new Formatter(exclude, escape, r.map, r.values)) {
                            lines.forEach(line -> writer.println(formatter.format(line)));
                        }
                    }
                } else {
                    Tool.copy(in, out.get(), new byte[1024]);
                }
            }), Tuple.of(Template.class, (r, out) -> {
                Template template = (Template) r.content;
                r.contentType(Tool.getContentType(template.name));
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get()));
                        Stream<String> lines = Tool.lines(Config.toURL(Config.app_template_folder.text(), template.name).get().openStream());
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, null)) {
                    lines.map(formatter::format).forEach(line -> {
                        Tool.printFormat(writer, line, template.replacer, "#{", "}", "${", "}", "<!--{", "}-->", "/*{", "}*/", "{/*", "*/}");
                        writer.println();
                    });
                }
            }));
}
