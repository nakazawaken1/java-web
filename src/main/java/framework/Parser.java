package framework;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Request parse
 */
@FunctionalInterface
public interface Parser {
    /**
     * @param text request text(not null, not empty)
     * @return map
     */
    Map<String, List<String>> parseImpl(String text);

    /**
     * @param text request text
     * @return map
     */
    default Map<String, List<String>> parse(String text) {
        if(text == null || text.length() <= 0) {
            return Tool.map();
        }
        return parseImpl(text);
    }

    /**
     * application/x-www-form-urlencoded
     */
    class Url implements Parser {

        @Override
        public Map<String, List<String>> parseImpl(String text) {
            return Stream.of(text.split("&")).map(s -> s.split("=", 2)).collect(Collectors.groupingBy(pair -> pair[0], Collectors.mapping(Try.f(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name())), Collectors.toList())));
        }
    }

    /**
     * application/json
     */
    class Json implements Parser {

        @Override
        public Map<String, List<String>> parseImpl(String text) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * text/csv
     */
    class Csv implements Parser {

        @Override
        public Map<String, List<String>> parseImpl(String text) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * application/xml
     */
    class Xml implements Parser {

        @Override
        public Map<String, List<String>> parseImpl(String text) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * application/yaml
     */
    class Yaml implements Parser {

        @Override
        public Map<String, List<String>> parseImpl(String text) {
            throw new UnsupportedOperationException();
        }
    }
}
