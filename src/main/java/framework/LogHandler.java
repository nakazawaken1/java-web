package framework;

import java.io.IOException;
import java.io.OutputStream;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * log handler
 * <ol>
 * <li>log file by level, date</li>
 * </ol>
 */
public class LogHandler extends Handler {

    /**
     * output map
     */
    protected final ConcurrentHashMap<Level, OutputStream> outMap;

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
    public LogHandler(String folder, DateTimeFormatter formatter) {
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
            Charset encoding = Optional.ofNullable(getEncoding()).map(Charset::forName).orElse(Charset.defaultCharset());
            outMap.computeIfAbsent(level, i -> {
                try {
                    Path path = Paths.get(folder, realFile);
                    Path parent = path.getParent();
                    if (Files.notExists(parent)) {
                        Files.createDirectories(parent);
                    }
                    return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception e) {
                    reportError(null, e, ErrorManager.OPEN_FAILURE);
                    return null;
                }
            }).write(message.getBytes(encoding));
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
        for (OutputStream i : outMap.values()) {
            try {
                i.flush();
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.Handler#close()
     */
    @Override
    public void close() throws SecurityException {
        for (Iterator<Map.Entry<Level, OutputStream>> i = outMap.entrySet().iterator(); i.hasNext();) {
            try {
                i.next().getValue().close();
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
    public static void startupLog() {
        try {
            Sys.Log.exclude.stream().forEach(i -> Logger.getLogger(i).setLevel(Level.SEVERE));
            Logger root = Logger.getLogger("");
            Level level = Sys.Log.level;
            root.setLevel(level);
            boolean noEntry = true;
            for (Handler i : root.getHandlers()) {
                if (i instanceof ConsoleHandler && !(i.getFormatter() instanceof LogFormatter)) {
                    i.setFormatter(new LogFormatter(Sys.Log.format));
                    i.setLevel(level);
                }
                if (i instanceof LogHandler) {
                    noEntry = false;
                }
            }
            if (noEntry) {
                if (first.compareAndSet(true, false)) {
                    handler = new LogHandler(Sys.Log.folder, Sys.Log.file_pattern);
                    handler.setLevel(level);
                    handler.setFormatter(new LogFormatter(Sys.Log.format));
                }
                root.addHandler(handler);
                Logger.getGlobal().config("addHandler: " + handler);
            }
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * log finalize
     */
    public static void shutdownLog() {
        Logger root = Logger.getLogger("");
        for (Handler i : root.getHandlers()) {
            if (i instanceof LogHandler) {
                Logger.getGlobal().config("removeHandler: " + i);
                i.close();
                root.removeHandler(i);
            }
        }
    }

}
