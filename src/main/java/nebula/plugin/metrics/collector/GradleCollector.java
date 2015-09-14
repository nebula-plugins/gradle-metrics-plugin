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
import nebula.plugin.metrics.MetricsPluginExtension;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.GradleToolContainer;
import nebula.plugin.metrics.model.Info;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.Task;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.profile.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Collector for Gradle.
 *
 * @author Danny Thomas
 */
public final class GradleCollector implements ProfileListener, BuildListener {
    private static final long TIMEOUT_MS = 5000;

    private final Logger logger = MetricsLoggerFactory.getLogger(GradleCollector.class);
    private final Supplier<MetricsDispatcher> dispatcherSupplier;
    private final AtomicBoolean complete = new AtomicBoolean();

    public GradleCollector(Supplier<MetricsDispatcher> dispatcherSupplier, MetricsPluginExtension extension) {
        this.dispatcherSupplier = checkNotNull(dispatcherSupplier);
        LoggingCollector.configureCollection(dispatcherSupplier, checkNotNull(extension));
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        checkNotNull(gradle);
        StartParameter startParameter = gradle.getStartParameter();
        checkState(!startParameter.isOffline(), "Collectors should not be registered when Gradle is running offline");
        try {
            dispatcherSupplier.get().startAsync().awaitRunning(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException | TimeoutException e) {
            logger.error("Error while starting metrics dispatcher. Metrics collection disabled.", e);
            return;
        }

        try {
            Project gradleProject = gradle.getRootProject();
            String name = gradleProject.getName();
            String version = String.valueOf(gradleProject.getVersion());
            nebula.plugin.metrics.model.Project project = new nebula.plugin.metrics.model.Project(name, version);
            MetricsDispatcher dispatcher = dispatcherSupplier.get();
            dispatcher.started(project); // We register this listener after the build has started, so we fire the start event here instead

            GradleToolContainer tool = GradleToolContainer.fromGradle(gradle);
            Plugin plugin = gradleProject.getPlugins().findPlugin("info-broker");
            if (plugin == null) {
                logger.info("Gradle info plugin not found. SCM and CI information will not be collected");
                dispatcher.environment(Info.create(tool));
            } else {
                GradleInfoCollector collector = new GradleInfoCollector(plugin);
                dispatcher.environment(Info.create(tool, collector.getSCM(), collector.getCI()));
            }
        } catch (Exception e) {
            logger.error("Unexpected exception in evaluation listener", e);
        }
    }

    @Override
    public void buildFinished(BuildResult buildResult) {
        Throwable failure = buildResult.getFailure();
        Result result = failure == null ? Result.success() : Result.failure(failure);
        logger.info("Build finished with result " + result);
        dispatcherSupplier.get().result(result);
        shutdownIfComplete();
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

        shutdownIfComplete();
    }

    /**
     * Conditionally shutdown the dispatcher, because Gradle listener event order appears to be non-deterministic.
     */
    private void shutdownIfComplete() {
        MetricsDispatcher dispatcher = this.dispatcherSupplier.get();
        if (!complete.compareAndSet(false, true)) {
            if (dispatcher.isRunning()) {
                logger.info("Shutting down dispatcher");
                try {
                    dispatcher.stopAsync().awaitTerminated(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    logger.error("Timed out after {}ms while waiting for metrics dispatcher to terminate", TIMEOUT_MS);
                } catch (IllegalStateException e) {
                    logger.error("Could not stop metrics dispatcher service", e);
                }
            }
            LoggingCollector.reset();
            Optional<String> receipt = dispatcher.receipt();
            if (receipt.isPresent()) {
                logger.warn(receipt.get());
            }
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

    @Override
    public void buildStarted(Gradle gradle) {
        checkNotNull(gradle);
    }

    @Override
    public void settingsEvaluated(Settings settings) {
        checkNotNull(settings);
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
        checkNotNull(gradle);
    }
}
