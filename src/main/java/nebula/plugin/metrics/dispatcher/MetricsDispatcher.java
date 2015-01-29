package nebula.plugin.metrics.dispatcher;

import com.google.common.util.concurrent.Service;

import nebula.plugin.metrics.model.*;

/**
 * @author Danny Thomas
 */
public interface MetricsDispatcher extends Service {
    void started(Project project);

    void duration(long startTime, long elapsedTime);

    void result(Result result);

    void event(String description, String type, long elapsedTime);

    void task(Task task);

    void log(LogEvent event);

    void test(Test test);

    void environment(Environment environment);
}
