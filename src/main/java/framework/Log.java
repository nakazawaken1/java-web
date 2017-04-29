package framework;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import app.config.Sys;
import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/**
 * log
 * <ol>
 * <li>log file by level, date</li>
 * </ol>
 */
@SuppressWarnings("restriction")
public class Log extends Handler {

    /**
     * log formatter
     */
    public static class Formatter extends java.util.logging.Formatter {

        /**
         * log format
         */
        protected String format;

        /**
         * logger-name editor
         */
        protected Function<String, String> editor;

        /**
         * constructor
         * 
         * @param format format
         * @param editor logger-name editor
         */
        public Formatter(String format, Function<String, String> editor) {
            this.format = format;
            this.editor = editor;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.logging.Formatter#format(java.util.logging.LogRecord) 1: timestamp 2: method 3: logger name 4: level 5: message 6: exception 7:
         * request id 8: session id 9: application id
         */
        @Override
        public String format(LogRecord record) {
            return String.format(format, record.getMillis(), record.getSourceClassName() + '.' + record.getSourceMethodName(),
                    editor.apply(record.getLoggerName()), record.getLevel().getName(), formatMessage(record),
                    Tool.of(record.getThrown()).map(t -> Tool.print(t::printStackTrace)).orElse(""), Request.current().map(Object::hashCode).orElse(0),
                    Session.current().map(Object::hashCode).orElse(0), Application.current().map(Object::hashCode).orElse(0));
        }

        /**
         * @param className Class name
         * @return compact package name
         */
        static String compact(String className) {
            String[] parts = className.split("[.]");
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (part.length() > 0) {
                    char c = part.charAt(0);
                    if (Character.isUpperCase(c)) {
                        break;
                    }
                    parts[i] = String.valueOf(c);
                }
            }
            return String.join(".", parts);
        }
    }

    /**
     * output map
     */
    protected final ConcurrentHashMap<Level, FileChannel> outMap;

    /**
     * output folder
     */
    protected final String folder;

    /**
     * current output file
     */
    protected String file;

    /**
     * formatter
     */
    protected final DateTimeFormatter formatter;

    /**
     * constructor
     * 
     * @param folder output folder
     * @param formatter log file formatter (DateTimeFormatter format, ll replace to log level, able to include folder)
     */
    public Log(String folder, DateTimeFormatter formatter) {
        outMap = new ConcurrentHashMap<>();
        this.folder = folder;
        this.formatter = formatter;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
     */
    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), ZoneId.systemDefault());
            String newFile = now.format(formatter);
            if (!newFile.equals(file)) {
                close();
                file = newFile;
            }
            Level level;
            String realFile;
            if (file.indexOf("ll") < 0) {
                level = Level.ALL;
                realFile = file;
            } else {
                level = record.getLevel();
                realFile = file.replace("ll", level.getName().toLowerCase());
            }
            String message = getFormatter().format(record);
            Charset encoding = Tool.of(getEncoding()).map(Charset::forName).orElse(Charset.defaultCharset());
            FileChannel channel = outMap.computeIfAbsent(level, i -> {
                FileChannel c = null;
                try {
                    Path path = Paths.get(folder, realFile);
                    Path parent = path.getParent();
                    if (Files.notExists(parent)) {
                        Files.createDirectories(parent);
                    }
                    c = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    if (!Sys.Log.is_shared && c.tryLock() == null) {
                        throw new Exception("lock failed: " + path);
                    }
                    System.err.println("log open #" + c.hashCode() + " : " + path);
                    return c;
                } catch (Exception e) {
                    if (c != null) {
                        Try.r(c::close, ee -> Log.warning(ee, () -> "close error")).run();
                    }
                    reportError(null, e, ErrorManager.OPEN_FAILURE);
                    return null;
                }
            });
            if (channel != null) {
                channel.write(ByteBuffer.wrap(message.getBytes(encoding)));
            }
        } catch (IOException e) {
            reportError(null, e, ErrorManager.WRITE_FAILURE);
            close();
        } catch (Exception e) {
            reportError(null, e, ErrorManager.FORMAT_FAILURE);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.Handler#flush()
     */
    @Override
    public void flush() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.Handler#close()
     */
    @Override
    public void close() throws SecurityException {
        for (Iterator<Map.Entry<Level, FileChannel>> i = outMap.entrySet().iterator(); i.hasNext();) {
            try {
                Tool.peek(i.next().getValue(), c -> System.err.println("log close #" + c.hashCode())).close();
                i.remove();
            } catch (Exception e) {
                reportError(null, e, ErrorManager.CLOSE_FAILURE);
            }
        }
    }

    /**
     * for initialize
     */
    private static final AtomicBoolean first = new AtomicBoolean(true);

    /**
     * log handler
     */
    private static volatile Handler handler;

    /**
     * log initialize
     */
    public static void startup() {
        Consumer<Handler> setup = handler -> {
            handler.setLevel(Sys.Log.level);
            handler.setFormatter(new Formatter(Sys.Log.format, Formatter::compact));
            if (!Sys.Log.ignore_prefixes.isEmpty()) {
                handler.setFilter(r -> Sys.Log.ignore_prefixes.stream().noneMatch(r.getLoggerName()::startsWith));
            }
        };
        try {
            Logger root = Logger.getLogger("");
            root.setLevel(Sys.Log.level);
            boolean noEntry = true;
            for (Handler i : root.getHandlers()) {
                if (i instanceof ConsoleHandler && !(i.getFormatter() instanceof Formatter)) {
                    setup.accept(i);
                }
                if (i instanceof Log) {
                    noEntry = false;
                }
            }
            if (noEntry) {
                if (first.compareAndSet(true, false)) {
                    handler = new Log(Sys.Log.folder, Sys.Log.file_pattern);
                    setup.accept(handler);
                }
                root.addHandler(handler);
                Logger.getLogger(Log.class.getCanonicalName()).config("addHandler: " + handler);
            }
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * log finalize
     */
    public static void shutdown() {
        Logger root = Logger.getLogger("");
        for (Handler i : root.getHandlers()) {
            if (i instanceof Log) {
                Logger.getGlobal().config("removeHandler: " + i);
                i.close();
                root.removeHandler(i);
            }
        }
    }

    /**
     * @param message message
     */
    public static void severe(String message) {
        log(Level.SEVERE, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void severe(Supplier<String> message) {
        log(Level.SEVERE, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void severe(Throwable thrown, Supplier<String> message) {
        log(Level.SEVERE, thrown, message);
    }

    /**
     * @param message message
     */
    public static void warning(String message) {
        log(Level.WARNING, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void warning(Supplier<String> message) {
        log(Level.WARNING, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void warning(Throwable thrown, Supplier<String> message) {
        log(Level.WARNING, thrown, message);
    }

    /**
     * @param message message
     */
    public static void info(String message) {
        log(Level.INFO, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void info(Supplier<String> message) {
        log(Level.INFO, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void info(Throwable thrown, Supplier<String> message) {
        log(Level.INFO, thrown, message);
    }

    /**
     * @param message message
     */
    public static void config(String message) {
        log(Level.CONFIG, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void config(Supplier<String> message) {
        log(Level.CONFIG, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void config(Throwable thrown, Supplier<String> message) {
        log(Level.CONFIG, thrown, message);
    }

    /**
     * @param message message
     */
    public static void fine(String message) {
        log(Level.FINE, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void fine(Supplier<String> message) {
        log(Level.FINE, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void fine(Throwable thrown, Supplier<String> message) {
        log(Level.FINE, thrown, message);
    }

    /**
     * @param message message
     */
    public static void finer(String message) {
        log(Level.FINER, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void finer(Supplier<String> message) {
        log(Level.FINER, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void finer(Throwable thrown, Supplier<String> message) {
        log(Level.FINER, thrown, message);
    }

    /**
     * @param message message
     */
    public static void finest(String message) {
        log(Level.FINEST, null, () -> message);
    }

    /**
     * @param message message
     */
    public static void finest(Supplier<String> message) {
        log(Level.FINEST, null, message);
    }

    /**
     * @param thrown Throwable associated with log message.
     * @param message The string message (or a key in the message catalog)
     */
    public static void finest(Throwable thrown, Supplier<String> message) {
        log(Level.FINEST, thrown, message);
    }

    /**
     * @param level Log level
     * @param thrown Throwable associated with log message.
     * @param message message The string message (or a key in the message catalog)
     */
    public static void log(Level level, Throwable thrown, Supplier<String> message) {
        int levelValue = level.intValue();
        if (level == Level.OFF) {
            return;
        }
        LogRecord record = new LogRecord(level, message.get());
        if (thrown != null) {
            record.setThrown(thrown);
        }
        JavaLangAccess access = SharedSecrets.getJavaLangAccess();
        Throwable throwable = new Throwable();
        int depth = access.getStackTraceDepth(throwable);
        for (int i = 0; i < depth; i++) {
            StackTraceElement frame = access.getStackTraceElement(throwable, i);
            String className = frame.getClassName();
            if (!Log.class.getName().equals(className)) {
                String methodName = frame.getMethodName();
                record.setSourceClassName(className);
                record.setSourceMethodName(methodName);
                record.setLoggerName(className + "." + methodName + "(" + frame.getLineNumber() + ")");
                break;
            }
        }
        for (Logger logger = Logger.getGlobal(); logger != null; logger = logger.getParent()) {
            for (Handler handler : logger.getHandlers()) {
                if (levelValue >= handler.getLevel().intValue() && Tool.of(handler.getFilter()).map(f -> f.isLoggable(record)).orElse(true)) {
                    handler.publish(record);
                }
            }
            if (!logger.getUseParentHandlers()) {
                break;
            }
        }
    }
}
