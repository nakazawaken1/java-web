package framework;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import framework.Try.TryBiConsumer;
import framework.Try.TryConsumer;

/**
 * Response
 */
public class Response {

    /**
     * response consumer
     */
    final TryConsumer<HttpServletResponse> consumer;

    /**
     * constructor
     * @param consumer response action
     */
    Response(TryConsumer<HttpServletResponse> consumer) {
        this.consumer = consumer;
    }

    /**
     * @param file file
     * @return response
     */
    public static Response file(String file) {
        Objects.requireNonNull(file);
        return new Response(r -> {
            r.setContentType(Tool.getContentType(file));
            r.getOutputStream().write(Files.readAllBytes(Paths.get(Config.toURL(Config.app_view_folder.text(), file).toURI())));
        });
    }

    /**
     * @param name part name
     * @return response
     */
    public static Response template(String name) {
        return template(name, null);
    }

    /**
     * @param name part name
     * @param replacer replace #{} to ?
     * @return response
     */
    public static Response template(String name, TryBiConsumer<PrintWriter, String> replacer) {
        Objects.requireNonNull(name);
        return new Response(r -> {
            try (PrintWriter writer = r.getWriter();
                    Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).toURI()))) {
                lines.forEach(line -> {
                    Tool.printFormat(writer, line, replacer, "#{", "}", "${", "}", "<!--{", "}-->");
                    writer.println();
                });
            }
        });
    }

    /**
     * @param out output with output stream
     * @return response
     */
    public static Response out(Consumer<OutputStream> out) {
        Objects.requireNonNull(out);
        return new Response(r -> {
            out.accept(r.getOutputStream());
        });
    }

    /**
     * @param write output with print writer
     * @return response
     */
    public static Response write(Consumer<PrintWriter> write) {
        Objects.requireNonNull(write);
        return new Response(r -> write.accept(r.getWriter()));
    }

    /**
     * 302
     * @param location location
     * @return response
     */
    public static Response redirect(String location) {
        Objects.requireNonNull(location);
        return new Response(r -> r.sendRedirect(location));
    }

    /**
     * @param text text
     * @return response
     */
    public static Response text(String text) {
        return new Response(r -> r.getWriter().print(text));
    }

    /**
     * write response
     */
    void flush() {
        HttpServletResponse response = Request.response.get();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> response.setHeader(i[0], i[1]));
        Try.c(consumer).accept(response);
    }
}
