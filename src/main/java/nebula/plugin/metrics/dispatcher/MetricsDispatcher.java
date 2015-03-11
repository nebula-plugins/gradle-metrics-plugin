package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.model.*;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.common.util.concurrent.Service;

/**
 * @author Danny Thomas
 */
public interface MetricsDispatcher extends Service {
    void started(Project project);

    void duration(long startTime, long elapsedTime);

    void result(Result result);

    void event(String description, String type, long elapsedTime);

    void task(Task task);

    void logbackEvent(LoggingEvent event);

    void test(Test test);

    void environment(Environment environment);
}
