package framework;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Try.TryTriConsumer;
import framework.annotation.Content;

/**
 * Response
 */
public abstract class Response {

    /**
     * instance creator
     */
    static Supplier<Response> factory;

    /**
     * logger
     */
    transient final Logger logger = Tool.getLogger();

    /**
     * encoding
     */
    Optional<Charset> charset = Optional.empty();

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
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    int status = 200;

    /**
     * content
     */
    Object content;

    /**
     * @param content content
     * @return response
     */
    @SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
    public static Response of(Object content) {
        return Tool.peek(factory.get(), r -> r.content = content);
    }

    /**
     * @param path location
     * @param status status code
     * @return response
     */
    public static Response redirect(String path, int status) {
        return factory.get().status(status).addHeader("Location", path);
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
        return factory.get().status(status);
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
        return of(writer).charset(StandardCharsets.UTF_8);
    }

    /**
     * @param file file
     * @return response
     */
    public static Response file(String file) {
        return of(Paths.get(Config.app_document_root_folder.text(), file));
    }

    /**
     * @param file file
     * @return response
     */
    public static Response template(String file) {
        return of(Paths.get(Config.app_template_folder.text(), file)).charset(StandardCharsets.UTF_8);
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
        Tool.addValue(headers, name, value);
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
        Tool.setValue(headers, name, value);
        return this;
    }

    /**
     * @param contentType content type
     * @param charset charset
     * @return self
     */
    public Response contentType(String contentType, Charset charset) {
        return setHeader("Content-Type", setCharset(contentType, charset));
    }

    /**
     * @param contentType content type
     * @return self
     */
    public Response contentType(String contentType) {
        return setHeader("Content-Type", contentType);
    }

    /**
     * @param charset charset
     * @return self
     */
    public Response charset(Charset charset) {
        this.charset = Optional.ofNullable(charset);
        return this;
    }

    /**
     * @return charset
     */
    public Charset charset() {
        return charset.orElse(StandardCharsets.UTF_8);
    }

    /**
     * @param contentType contentType
     * @param charset charset
     * @return contentType with charset
     */
    public static String setCharset(String contentType, Charset charset) {
        if (!Tool.string(contentType).isPresent() || charset == null) {
            return contentType;
        }
        boolean[] unset = { true };
        String result = Stream.of(contentType.split("\\s*;\\s*")).map(part -> {
            if (Tool.splitAt(part, "\\s*=\\s*", 0).equalsIgnoreCase("charset")) {
                unset[0] = false;
                return "charset=" + charset.name();
            } else {
                return part;
            }
        }).collect(Collectors.joining("; "));
        return unset[0] ? result + "; charset=" + charset.name() : result;
    }

    /**
     * write response
     */
    void flush() {
        boolean[] cancel = { false };
        writeResponse(Try.c(out -> {
            for (Tuple<Class<?>, TryTriConsumer<Response, Supplier<OutputStream>, boolean[]>> pair : writers) {
                if (pair.l.isAssignableFrom(content.getClass())) {
                    pair.r.accept(this, out, cancel);
                    if (!cancel[0]) {
                        break;
                    }
                }
            }
        }));
        logger.info(toString());
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
    static final List<Tuple<Class<?>, TryTriConsumer<Response, Supplier<OutputStream>, boolean[]>>> writers = Arrays
            .asList(Tuple.of(String.class, (response, out, cancel) -> {
                response.contentType(Content.HTML, response.charset.orElse(null));
                out.get().write(((String) response.content).getBytes(response.charset()));
            }), Tuple.of(Writer.class, (response, out, cancel) -> {
                response.contentType(Content.HTML, response.charset.orElse(null));
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get(), response.charset()))) {
                    ((Writer) response.content).write(writer);
                }
            }), Tuple.of(Output.class, (response, out, cancel) -> ((Output) response.content).output(out.get())),
                    Tuple.of(Path.class, (response, out, cancel) -> {
                        String file = ((Path) response.content).toString().replace('\\', '/');
                        Optional<URL> url = Config.toURL(file);
                        if (url.isPresent()) {
                            try (InputStream in = url.get().openStream()) {
                                if (Try.s(() -> in.available() >= 0, e -> false).get()) {
                                    response.contentType(Tool.getContentType(file),
                                            response.charset.orElseGet(() -> Tool.isTextContent(file) ? StandardCharsets.UTF_8 : null));
                                    if (Config.app_format_include_regex.stream().anyMatch(file::matches)
                                            && Config.app_format_exclude_regex.stream().noneMatch(file::matches)) {
                                        try (Stream<String> lines = Tool.lines(in);
                                                PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get(), response.charset()))) {
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
                                            try (Formatter formatter = new Formatter(exclude, escape, response.map, response.values)) {
                                                lines.forEach(line -> writer.println(formatter.format(line)));
                                            }
                                        }
                                    } else {
                                        Tool.copy(in, out.get(), new byte[1024]);
                                    }
                                } else {
                                    String prefix = Paths.get(Config.app_document_root_folder.text()).toString().replace('\\', '/');
                                    String path = file.startsWith(prefix) ? file.substring(prefix.length()) : file;
                                    Tool.getLogger().info(file + " : " + path);
                                    response.setHeader("Location",
                                            Tool.trim(null, Application.current().get().getContextPath(), "/") + Tool.suffix(path, "/") + "index.html")
                                            .status(301);
                                    out.get();
                                }
                            }
                            return;
                        }

                        /* no content */
                        if (Arrays.asList(".css", ".js").contains(Tool.getExtension(file))) {
                            response.status(204);
                        } else {
                            Tool.getLogger().info("not found: " + Tool.trim("/", file, null));
                            response.status(404);
                        }
                        out.get();
                    }), Tuple.of(Template.class, (response, out, cancel) -> {
                        Template template = (Template) response.content;
                        response.contentType(Tool.getContentType(template.name), response.charset());
                        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get(), response.charset()));
                                Stream<String> lines = Tool.lines(Config.toURL(Config.app_template_folder.text(), template.name).get().openStream());
                                Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, null)) {
                            lines.map(formatter::format).forEach(line -> {
                                Tool.printFormat(writer, line, template.replacer, "#{", "}", "${", "}", "<!--{", "}-->", "/*{", "}*/", "{/*", "*/}");
                                writer.println();
                            });
                        }
                    }), Tuple.of(Object.class, (response, out, cancel) -> {
                        Runnable other = Try.r(() -> {
                            response.contentType(Content.TEXT, response.charset());
                            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get(), response.charset()))) {
                                if (response.content instanceof Stream) {
                                    ((Stream<?>) response.content).map(Tool::dump).forEach(writer::println);
                                } else if (response.content instanceof Optional) {
                                    ((Optional<?>) response.content).map(Tool::dump).ifPresent(writer::print);
                                } else {
                                    writer.print(Tool.dump(response.content));
                                }
                            }
                        });
                        Tool.ifPresentOr(response.headers.getOrDefault("Content-Type", Arrays.asList()).stream().findFirst(), Try.c(contentType -> {
                            switch (Tool.splitAt(contentType, "\\s*;", 0)) {
                            case Content.JSON:
                                Tool.json(response.content, out.get());
                                break;
                            default:
                                other.run();
                                break;
                            }
                        }), other);
                    }));
}
