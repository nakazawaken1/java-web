package framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

/**
 * Message
 */
public enum Message {
    /*define message key here*/
    
    /**
     * sample
     */
    text_hello,
    
    alert_forbidden,
    
    ;

    /**
     * messages from message.txt
     * <div>The locale setting can be specified in the server startup parameter - app.locale</div>
     */
    public static final ResourceBundle messages = ResourceBundle.getBundle("message",
            Optional.ofNullable(System.getProperty("app.locale")).map(Locale::new).orElse(Locale.getDefault()), Thread.currentThread().getContextClassLoader(),
            new Control() {
                /*
                 * (non-Javadoc)
                 * 
                 * @see java.util.ResourceBundle.Control#newBundle(java.lang.String, java.util.Locale, java.lang.String, java.lang.ClassLoader, boolean)
                 */
                @Override
                public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                        throws IllegalAccessException, InstantiationException, IOException {
                    String resourceName = toResourceName(toBundleName(baseName, locale), "txt");

                    try (InputStream in = loader.getResourceAsStream(resourceName);
                            InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        return new PropertyResourceBundle(reader);
                    }
                }
            });

    /**
     * get message from id
     * 
     * @param id message id
     * @return message or id if missing
     */
    public static Optional<String> find(String id) {
        try {
            return Optional.of(messages.getString(id));
        } catch (MissingResourceException e) {
            return Optional.empty();
        }
    }
    
    /**
     * @return id
     */
    public String id() {
        return name().replace('_', '.');
    }

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return find(id()).orElseGet(this::id);
    }
}
