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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import app.config.Sys;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import framework.Tool.CsvTraverser;
import framework.Tool.JsonTraverser;
import framework.Tool.Traverser;
import framework.Tool.XmlTraverser;
import framework.Try.TryTriConsumer;
import framework.annotation.Content;
import framework.annotation.Letters;

/**
 * Response
 */
public abstract class Response {
    /**
     * HTTP Status
     */
    @SuppressWarnings("javadoc")
    public enum Status {
        OK(200),
        Created(201),
        Accepted(202),
        NonAuthoritative_Information(203),
        No_Content(204),
        Reset_Content(205),
        Partial_Content(206),
        Multiple_Choices(300),
        Moved_Permamently(301),
        Found(302),
        See_Other(303),
        Not_Modified(304),
        Use_Proxy(305),
        Temporary_Rediret(307),
        Permanent_Redirect(308),
        Bad_Request(400),
        Unauthorized(401),
        Payment_Required(402),
        Forbidden(403),
        Not_Found(404),
        Method_Not_Allowed(405),
        Not_Acceptable(406),
        Proxy_Authentication_Required(407),
        Request_Timeout(408),
        Conflict(409),
        Gone(410),
        Length_Required(411),
        Precondition_Failed(412),
        Payload_Too_large(413),
        URI_Too_Long(414),
        Unsupported_Media_Type(415),
        Range_Not_Satisfiable(416),
        Expectation_Failed(417),
        Misdirected_Request(421),
        Internal_Server_Error(500),
        Not_Implemented(501),
        Bad_Gateway(502),
        Service_Unavailable(503),
        Gateway_Timeout(504),
        HTTP_Version_Not_Supported(505);

        /**
         * Status code
         */
        int code;

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            // _ -> space, AbcDef -> Abc-Def
            IntFunction<IntStream> m = c -> (Letters.ALPHABET_UPPERS.indexOf(c) >= 0 ? IntStream.of('-', c) : IntStream.of(c));
            Function<String, StringBuilder> mapper = i -> IntStream.concat(IntStream.of(i.charAt(0)), i.chars().skip(1).flatMap(m)).collect(StringBuilder::new,
                    (s, c) -> s.append((char) c), StringBuilder::append);
            String name = name();
            int max = name.length();
            int skip = 0;
            while (skip < max && Character.isUpperCase(name.charAt(skip))) {
                skip++;
            }
            skip--;
            return code + " " + name.substring(0, skip) + Stream.of(name.substring(skip).split("_")).map(mapper).collect(Collectors.joining(" "));
        }

        /**
         * @param code Status code
         * @return Status
         */
        public static Optional<Status> of(int code) {
            return Stream.of(values()).filter(i -> i.code == code).findAny();
        }

        /**
         * @param code Status code
         */
        private Status(int code) {
            this.code = code;
        }
    }

    /**
     * instance creator
     */
    static Supplier<Response> factory;

    /**
     * encoding
     */
    Optional<Charset> charset = Optional.empty();

    /**
     * locale
     */
    Optional<Locale> locale = Optional.empty();

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
    Status status = Status.OK;

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
    public static Response redirect(String path, Status status) {
        return factory.get().status(status).addHeader("Location", path);
    }

    /**
     * @param path location
     * @return response
     */
    public static Response redirect(String path) {
        return redirect(path, Status.Found);
    }

    /**
     * @param status status code
     * @return response
     */
    public static Response error(Status status) {
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
        return of(Paths.get(Sys.document_root_folder, file));
    }

    /**
     * @param file file
     * @return response
     */
    public static Response template(String file) {
        return of(Paths.get(Sys.template_folder, file)).charset(StandardCharsets.UTF_8);
    }

    /**
     * @param status status
     * @return status
     */
    public Response status(Status status) {
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
        this.charset = Tool.of(charset);
        return this;
    }

    /**
     * @return charset
     */
    public Charset charset() {
        return charset.orElse(StandardCharsets.UTF_8);
    }

    /**
     * @param locale locale
     * @return self
     */
    public Response locale(Locale locale) {
        this.locale = Tool.of(locale);
        return this;
    }

    /**
     * @return locale
     */
    public Locale locale() {
        return locale.orElseGet(Session::currentLocale);
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
        boolean[] cancel = { false }; // process next writer if true
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
        Log.info(this::toString);
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
         * @return Template
         */
        public static Template of(String name, TryTriConsumer<PrintWriter, String, String> replacer) {
            Template t = new Template();
            t.name = name;
            t.replacer = replacer;
            return t;
        }
    }

    /**
     * Render
     */
    public static class Render {
        /**
         * File
         */
        String file;
        /**
         * Renders
         */
        Map<String, Function<Xml, Xml>> renders;

        /**
         * @param file File
         * @param renders Function(Xml, Xml)
         * @return Render
         */
        @SafeVarargs
        public static Render of(String file, Function<Xml, Xml>... renders) {
            Render r = new Render();
            r.file = file;
            r.renders = Stream.of(renders).collect(LinkedHashMap::new, (map, render) -> map.put(String.valueOf(map.size()), render), Map::putAll);
            return r;
        }

        /**
         * @param file File
         * @param renders (name: Function(Xml, Xml))
         * @return Render
         */
        public static Render ofMap(String file, Map<String, Function<Xml, Xml>> renders) {
            Render r = new Render();
            r.file = file;
            r.renders = renders;
            return r;
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
     * Traversers
     */
    public Map<Class<? extends Traverser>, Traverser> traverserMap;

    /**
     * @param <T> Traverser type
     * @param clazz Traverser class
     * @param setup Setup
     * @return Self
     */
    @SuppressWarnings("unchecked")
    public <T extends Traverser> Response traverser(Class<T> clazz, Consumer<T> setup) {
        if (traverserMap == null) {
            traverserMap = new HashMap<>();
        }
        setup.accept((T) traverserMap.computeIfAbsent(clazz, Try.f(Class::newInstance)));
        return this;
    }

    /**
     * @param <T> Traverser type
     * @param clazz Traverser class
     * @return Traverser
     */
    @SuppressWarnings("unchecked")
    public <T extends Traverser> T traverser(Class<T> clazz) {
        return Tool.of(traverserMap).map(i -> (T) i.get(clazz)).orElseGet(Try.s(clazz::newInstance));
    }

    /**
     * body writer
     */
    public static final List<Tuple<Class<?>, TryTriConsumer<Response, Supplier<OutputStream>, boolean[]>>> writers = Tool.list(//
            Tuple.of(String.class, (response, out, cancel) -> {
                response.contentType(Content.TEXT, response.charset.orElse(StandardCharsets.UTF_8));
                out.get().write(((String) response.content).getBytes(response.charset()));
            }), //
            Tuple.of(Writer.class, (response, out, cancel) -> {
                response.contentType(Content.TEXT, response.charset.orElse(StandardCharsets.UTF_8));
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get(), response.charset()))) {
                    ((Writer) response.content).write(writer);
                }
            }), //
            Tuple.of(Output.class, (response, out, cancel) -> ((Output) response.content).output(out.get())), //
            Tuple.of(Path.class, (response, out, cancel) -> {
                String file = ((Path) response.content).toString().replace('\\', '/');
                Optional<URL> url = Tool.toURL(file);
                if (url.isPresent()) {
                    URL u = url.get();
                    try (InputStream in = u.openStream()) {
                        if (!("file".equals(u.getProtocol()) && Paths.get(u.toURI()).toFile().isDirectory())
                                && Try.s(() -> in.available() >= 0, e -> false).get()) {
                            Log.config("[static load] " + u);
                            response.contentType(Tool.getContentType(file),
                                    response.charset.orElseGet(() -> Tool.isTextContent(file) ? StandardCharsets.UTF_8 : null));
                            if (Sys.format_include_regex.matcher(file).matches() && !Sys.format_exclude_regex.matcher(file).matches()) {
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
                                        escape = Tool::htmlEscape;
                                    }
                                    try (Formatter formatter = new Formatter(exclude, escape, response.locale(), response.map, response.values)) {
                                        lines.forEach(line -> writer.println(formatter.format(line)));
                                    }
                                }
                            } else {
                                Tool.copy(in, out.get(), new byte[1024]);
                            }
                        } else {
                            String prefix = Paths.get(Sys.document_root_folder).toString().replace('\\', '/');
                            String path = file.startsWith(prefix) ? file.substring(prefix.length()) : file;
                            Log.info(file + " : " + path);
                            response.setHeader("Location",
                                    Tool.trim(null, Application.current().get().getContextPath(), "/") + Tool.suffix(path, "/") + "index.html")
                                    .status(Status.Moved_Permamently);
                            out.get();
                        }
                    } catch (NullPointerException e) {
                        /* noop */
                    }
                    return;
                }

                /* no content */
                if (Tool.list(".css", ".js").contains(Tool.getExtension(file))) {
                    response.status(Status.No_Content);
                } else if (Paths.get(Sys.document_root_folder, "index.html").toString().replace('\\', '/').equals(file)) {
                    response.setHeader("Location", Tool.trim(null, Application.current().get().getContextPath(), "/") + "admin/index.html")
                            .status(Status.Moved_Permamently);
                } else {
                    Log.info("not found: " + Tool.trim("/", file, null));
                    response.status(Status.Not_Found);
                }
                out.get();
            }), //
            Tuple.of(Template.class, (response, out, cancel) -> {
                Template template = (Template) response.content;
                response.contentType(Tool.getContentType(template.name), response.charset());
                URL url = (template.name.startsWith("/") ? Tool.toURL(template.name) : Tool.toURL(Sys.template_folder, template.name)).get();
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out.get(), response.charset()));
                        Stream<String> lines = Tool.lines(url.openStream());
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Tool::htmlEscape, response.locale(), null)) {
                    lines.map(formatter::format).forEach(line -> {
                        Tool.printFormat(writer, line, template.replacer, "#{", "}", "${", "}", "<!--{", "}-->", "/*{", "}*/", "{/*", "*/}");
                        writer.println();
                    });
                }
            }), //
            Tuple.of(Render.class, (response, out, cancel) -> {
                Render render = (Render) response.content;
                response.contentType(Tool.getContentType(render.file), response.charset());
                URL url = (render.file.startsWith("/") ? Tool.toURL(render.file) : Tool.toURL(Sys.template_folder, render.file)).get();
                try (InputStream in = url.openStream();
                        Formatter formatter = new Formatter(Formatter::excludeForHtml, Tool::htmlEscape, response.locale(), null)) {
                    out.get().write(Xml.parseMap(formatter.format(Tool.loadText(in)), render.renders).toString().getBytes(response.charset()));
                }
            }), //
            Tuple.of(Object.class, (response, out, cancel) -> {
                Runnable other = Try.r(() -> {
                    response.contentType(Content.TEXT, response.charset());
                    Tool.csv(response.content, out.get(), response.charset());
                });
                Tool.ifPresentOr(response.headers.getOrDefault("Content-Type", Tool.list()).stream().findFirst(), Try.c(contentType -> {
                    switch (Tool.splitAt(contentType, "\\s*;", 0)) {
                    case Content.JSON:
                    case Content.YML:
                        Tool.traverse(response.content, Tool.peek(response.traverser(JsonTraverser.class), t -> {
                            t.out = out.get();
                            t.charset = response.charset();
                        }));
                        break;
                    case Content.XML:
                        Tool.traverse(response.content, Tool.peek(response.traverser(XmlTraverser.class), t -> {
                            t.out = out.get();
                            t.charset = response.charset();
                        }));
                        break;
                    case Content.CSV:
                        OutputStream o = out.get();
                        o.write(Tool.BOM);
                        Tool.traverse(response.content, Tool.peek(response.traverser(CsvTraverser.class), t -> {
                            t.out = o;
                            t.charset = response.charset();
                        }));
                        break;
                    case Content.TSV:
                        Tool.traverse(response.content, Tool.peek(response.traverser(CsvTraverser.class), t -> {
                            t.out = out.get();
                            t.charset = response.charset();
                            t.separator = '\t';
                            t.clouser = '\0';/* none */
                            t.innerSeparator = ",";
                        }));
                        break;
                    default:
                        other.run();
                        break;
                    }
                }), other);
            }));

    /**
     * @param args Not use
     */
    public static void main(String[] args) {
        Stream.of(Status.values()).forEach(System.out::println);
    }
}
