package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.Task;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.tasks.TaskState;
import org.gradle.profile.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector for Gradle build profiling metrics.
 *
 * @author Danny Thomas
 */
public class GradleProfileCollector implements ProfileListener {
    private static final Logger logger = LoggerFactory.getLogger(GradleProfileCollector.class);
    private final MetricsDispatcher dispatcher;

    public GradleProfileCollector(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    @Override
    public void buildFinished(BuildProfile result) {
        checkNotNull(result);
        long startupElapsed = result.getElapsedStartup();
        long settingsElapsed = result.getElapsedSettings();
        long loadingElapsed = result.getElapsedProjectsLoading();

        // Initialisation
        dispatcher.event("startup", "init", startupElapsed);
        long expectedTotal = startupElapsed;

        // Configuration
        dispatcher.event("settings", "configure", settingsElapsed);
        expectedTotal += settingsElapsed;
        dispatcher.event("projectsLoading", "configure", loadingElapsed);
        expectedTotal += loadingElapsed;
        for (ProjectProfile projectProfile : result.getProjects()) {
            ContinuousOperation configurationOperation = projectProfile.getConfigurationOperation();
            long configurationElapsed = configurationOperation.getElapsedTime();
            dispatcher.event(configurationOperation.getDescription(), "configure", configurationElapsed);
            expectedTotal += configurationElapsed;
        }

        // Resolve
        for (ContinuousOperation operation : result.getDependencySets()) {
            long resolveElapsed = operation.getElapsedTime();
            dispatcher.event(operation.getDescription(), "resolve", resolveElapsed);
            expectedTotal += resolveElapsed;
        }

        // Execution
        for (ProjectProfile projectProfile : result.getProjects()) {
            long totalTaskElapsed = 0;
            CompositeOperation<TaskExecution> tasks = projectProfile.getTasks();
            for (TaskExecution execution : tasks.getOperations()) {
                Result taskResult = getTaskExecutionResult(execution);
                long taskElapsed = execution.getElapsedTime();
                Task task = Task.create(execution.getDescription(), taskResult, execution.getStartTime(), taskElapsed);
                dispatcher.task(task);
                totalTaskElapsed += taskElapsed;
            }
            dispatcher.event("task", "execution", totalTaskElapsed);
            expectedTotal += totalTaskElapsed; // totalTaskElapsed is equal to result.getElapsedTotalExecutionTime()
        }

        long elapsedTotal = result.getElapsedTotal();
        dispatcher.duration(result.getBuildStarted(), elapsedTotal);

        // Check the totals agree with the aggregate elapsed times, and log an event with the difference if not
        if (elapsedTotal != expectedTotal) {
            long difference = expectedTotal - elapsedTotal;
            logger.info("Total build time of {}ms does not match the actual calculated total of {}ms (difference: {}ms). Creating event with type 'other'", expectedTotal, elapsedTotal, difference);
            dispatcher.event("unknown", "other", difference); // FIXME this appears to always be the case, investigate
        }
    }

    @VisibleForTesting
    Result getTaskExecutionResult(TaskExecution taskExecution) {
        Result result = Result.success();
        TaskState state = taskExecution.getState();
        if (!state.getDidWork()) {
            result = Result.skipped();
        } else {
            //noinspection ThrowableResultOfMethodCallIgnored
            Throwable failure = state.getFailure();
            if (failure != null) {
                result = Result.failure(failure);
            }
        }
        return result;
    }
}
