/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.Task;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import org.gradle.api.tasks.TaskState;
import org.gradle.profile.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector for Gradle build profiling metrics.
 *
 * @author Danny Thomas
 */
public final class GradleProfileCollector implements ProfileListener {
    private static final long SHUTDOWN_TIMEOUT_MS = 5000;

    private final Logger logger = MetricsLoggerFactory.getLogger(GradleProfileCollector.class);
    private final Supplier<MetricsDispatcher> dispatcherSupplier;

    public GradleProfileCollector(Supplier<MetricsDispatcher> dispatcherSupplier) {
        this.dispatcherSupplier = checkNotNull(dispatcherSupplier);
    }

    @Override
    public void buildFinished(BuildProfile result) {
        checkNotNull(result);
        long startupElapsed = result.getElapsedStartup();
        long settingsElapsed = result.getElapsedSettings();
        long loadingElapsed = result.getElapsedProjectsLoading();

        // Initialisation
        MetricsDispatcher dispatcher = this.dispatcherSupplier.get();
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
                Task task = new Task(execution.getDescription(), taskResult, new DateTime(execution.getStartTime()), taskElapsed);
                dispatcher.task(task);
                totalTaskElapsed += taskElapsed;
            }
            dispatcher.event("task", "execution", totalTaskElapsed);
            expectedTotal += totalTaskElapsed; // totalTaskElapsed is equal to result.getElapsedTotalExecutionTime()
        }

        long elapsedTotal = result.getElapsedTotal();
        dispatcher.duration(result.getBuildStarted(), elapsedTotal);

        // Check the totals agree with the aggregate elapsed times, and log an event with the difference if not
        // For instance, Gradle doesn't account for the time taken to download artifacts: http://forums.gradle.org/gradle/topics/profile-report-doesnt-account-for-time-spent-downloading-dependencies
        if (elapsedTotal < expectedTotal) {
            long difference = expectedTotal - elapsedTotal;
            logger.info("Total build time of {}ms is less than the calculated total of {}ms (difference: {}ms). Creating 'unknown' event with type 'other'", expectedTotal, elapsedTotal, difference);
            dispatcher.event("unknown", "other", difference);
        }

        // This always appears to be called after the build result listener, so we shutdown here
        logger.info("Shutting down dispatcher");
        if (dispatcher.isRunning()) {
            try {
                dispatcher.stopAsync().awaitTerminated(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.error("Timed out after {}ms while waiting for metrics dispatcher to terminate", SHUTDOWN_TIMEOUT_MS);
            } catch (IllegalStateException e) {
                logger.error("Could not stop metrics dispatcher service", e);
            }
        }
        LogbackCollector.resetLogbackCollection();
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
