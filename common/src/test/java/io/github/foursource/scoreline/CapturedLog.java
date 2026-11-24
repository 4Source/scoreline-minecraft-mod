package io.github.foursource.scoreline;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures what the {@code scoreline} logger and its children write while it is open, down to DEBUG.
 * <p>
 * It gives {@code scoreline} a logger configuration of its own with an appender that keeps every event; {@link #close()} removes that configuration again, so later tests see the test configuration's
 * levels unchanged.
 */
public final class CapturedLog implements AutoCloseable {
    private final LoggerContext context;
    private final ListAppender appender;

    private CapturedLog(LoggerContext context, ListAppender appender) {
        this.context = context;
        this.appender = appender;
    }

    /**
     * Starts capturing.
     *
     * @return the open capture; close it with try-with-resources
     */
    public static CapturedLog start() {
        LoggerContext context = LoggerContext.getContext(false);
        Configuration config = context.getConfiguration();
        ListAppender appender = new ListAppender();
        appender.start();
        // Additive, so the lines still reach the console appender of the root logger.
        LoggerConfig own = new LoggerConfig(Constants.MOD_ID, Level.DEBUG, true);
        own.addAppender(appender, null, null);
        config.addLogger(Constants.MOD_ID, own);
        context.updateLoggers();
        return new CapturedLog(context, appender);
    }

    /**
     * The captured events in order, each as {@code <LEVEL> [<logger>] <formatted message>}, for example {@code INFO [scoreline] Scoreline 1.2.3 loaded}.
     *
     * @return a copy of the lines captured so far
     */
    public List<String> lines() {
        return List.copyOf(appender.lines);
    }

    @Override
    public void close() {
        context.getConfiguration().removeLogger(Constants.MOD_ID);
        context.updateLoggers();
        appender.stop();
    }

    private static final class ListAppender extends AbstractAppender {
        private final List<String> lines = new CopyOnWriteArrayList<>();

        ListAppender() {
            super("CapturedLog", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lines.add(event.getLevel() + " [" + event.getLoggerName() + "] " + event.getMessage().getFormattedMessage());
        }
    }
}
