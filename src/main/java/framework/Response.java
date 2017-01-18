package framework;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import framework.Try.TryConsumer;
import framework.Try.TryTriConsumer;

/**
 * Response
 */
public class Response {

    /**
     * response consumer
     */
    final TryConsumer<HttpServletResponse> consumer;
    
    /**
     * encoding
     */
    static Charset charset = StandardCharsets.UTF_8;
    
    /**
     * 属性
     */
    Map<String, Object> map;

    /**
     * constructor
     * @param consumer response action
     */
    Response(TryConsumer<HttpServletResponse> consumer) {
        this.consumer = consumer;
    }

    /**
     * @param file file
     * @param values values
     * @return response
     */
    public static Response file(String file, Object...values) {
        Objects.requireNonNull(file);
        Map<String, Object> map = new HashMap<>();
        return new Response(r -> {
            r.setContentType(Tool.getContentType(file));
        	Path path = Paths.get(Config.toURL(Config.app_view_folder.text(), file).get().toURI());
        	if(Config.app_format_include_regex.stream().anyMatch(file::matches) && Config.app_format_exclude_regex.stream().noneMatch(file::matches)) {
        		try(Stream<String> lines = Files.lines(path); PrintWriter writer = r.getWriter()) {
        			Function<Formatter, Formatter.Result> exclude;
        			Function<Object, String> escape;
        			if(file.endsWith(".js")) {
        				exclude = Formatter::excludeForScript;
        				escape = Formatter::scriptEscape;
        			} else if(file.endsWith(".css")) {
        				exclude = Formatter::excludeForStyle;
        				escape = null;
        			} else {
        				exclude = Formatter::excludeForHtml;
        				escape = Formatter::htmlEscape;
        			}
        			Formatter formatter = new Formatter(exclude, escape, map, values);
        			lines.forEach(line -> writer.println(formatter.format(line)));
        		}
        	} else {
        		r.getOutputStream().write(Files.readAllBytes(path));
        	}
        }).bind(map);
    }
    
    /**
     * @param key key
     * @param value value
     * @return self
     */
    public Response bind(String key, Object value) {
    	if(map == null) {
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
    	if(this.map == null) {
    		this.map = map;
    	} else {
    		this.map.putAll(map);
    	}
    	return this;
    }

    /**
     * @param name part name
     * @param replacer replacer(writer, expression, prefix)
     * @return response
     */
    public static Response writeTemplate(String name, TryTriConsumer<PrintWriter, String, String> replacer) {
        Objects.requireNonNull(name);
        return new Response(r -> {
            r.setContentType("text/html;charset=" + charset);
            try (PrintWriter writer = r.getWriter();
                    Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).get().toURI()))) {
                lines.forEach(line -> {
                    Tool.printFormat(writer, line, replacer, "#{", "}", "${", "}", "<!--{", "}-->", "/*{", "}*/", "{/*", "*/}");
                    writer.println();
                });
            }
        });
    }

    /**
     * @param name part name
     * @param values values
     * @return response
     */
    public static Response template(String name, Object...values) {
        Objects.requireNonNull(name);
        Map<String, Object> map = new HashMap<>();
        return new Response(r -> {
            r.setContentType("text/html;charset=" + charset);
            try (PrintWriter writer = r.getWriter();
                    Stream<String> lines = Files.lines(Paths.get(Config.toURL(Config.app_template_folder.text(), name).get().toURI()))) {
            	Formatter formatter = new Formatter(Formatter::excludeForHtml, Formatter::htmlEscape, map, values);
                lines.forEach(line -> writer.println(formatter.format(line)));
            }
        }).bind(map);
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
        return new Response(r -> {
            r.setContentType("text/plain;charset=" + charset);
            r.getWriter().print(text);
        });
    }

    /**
     * write response
     */
    void flush() {
        HttpServletResponse response = Request.response.get();
        response.setCharacterEncoding(charset.name());
        Config.app_headers.stream().map(i -> i.split("\\s*\\:\\s*", 2)).forEach(i -> response.setHeader(i[0], i[1]));
        Try.c(consumer).accept(response);
    }

    /**
     * @param o object
     * @return response
     */
    public static Response json(Object o) {
        return new Response(r -> {
            r.setContentType("application/json;charset=" + charset);
            r.getWriter().print(Tool.json(o));
        });
    }
}
