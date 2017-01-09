package framework;

import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * log formatter
 */
public class LogFormatter extends Formatter {

    /**
     * log format
     */
    protected String format;

    /**
     * constructor
     * 
     * @param format format
     */
    public LogFormatter(String format) {
        this.format = format;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
     */
    @Override
    public String format(LogRecord record) {
        return String.format(format, record.getMillis(), record.getSourceClassName() + '.' + record.getSourceMethodName(), record.getLoggerName(),
                record.getLevel().getName(), formatMessage(record),
                Optional.ofNullable(record.getThrown()).map(t -> Tool.print(t::printStackTrace)).orElse(""));
    }

}
