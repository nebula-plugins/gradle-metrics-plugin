package nebula.plugin.metrics.collector;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.LogEvent;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector that intercepts Logback events by binding an appender to a provided {@link Logger}.
 *
 * @author Danny THomas
 */
public class LogbackAppenderCollector {
    public static void addLogbackAppender(final MetricsDispatcher dispatcher, Class<?> loggerClass) {
        checkNotNull(dispatcher);
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent iLoggingEvent) {
                LogEvent event = LogEvent.create(iLoggingEvent.getFormattedMessage());
                dispatcher.log(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
    }
}
