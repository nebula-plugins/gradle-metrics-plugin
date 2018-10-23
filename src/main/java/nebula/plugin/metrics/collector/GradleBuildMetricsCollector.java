/*
 *  Copyright 2015-2018 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package nebula.plugin.metrics.collector;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import nebula.plugin.info.InfoBrokerPlugin;
import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.GradleToolContainer;
import nebula.plugin.metrics.model.Info;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.BuildMetrics;
import nebula.plugin.metrics.model.CompositeOperation;
import nebula.plugin.metrics.model.ContinuousOperation;
import nebula.plugin.metrics.model.ProjectMetrics;
import nebula.plugin.metrics.model.TaskExecution;
import nebula.plugin.metrics.time.BuildStartedTime;
import nebula.plugin.metrics.time.Clock;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.joda.time.DateTime;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

public final class GradleBuildMetricsCollector implements BuildListener, ProjectEvaluationListener, TaskExecutionListener, DependencyResolutionListener {

    private static final long TIMEOUT_MS = 5000;

    private final Logger logger = MetricsLoggerFactory.getLogger(GradleBuildMetricsCollector.class);
    private final Supplier<MetricsDispatcher> dispatcherSupplier;

    private final AtomicBoolean buildProfileComplete = new AtomicBoolean(false);
    private final AtomicBoolean buildResultComplete = new AtomicBoolean(false);

    public GradleBuildMetricsCollector(Supplier<MetricsDispatcher> dispatcherSupplier, BuildStartedTime buildStartedTime, Clock clock) {
        checkNotNull(dispatcherSupplier);
        checkNotNull(buildStartedTime);
        checkNotNull(clock);
        this.dispatcherSupplier = checkNotNull(dispatcherSupplier);
        this.buildStartedTime = buildStartedTime;
        this.clock = clock;
    }

    private final BuildStartedTime buildStartedTime;
    private final Clock clock;
    private BuildMetrics buildMetrics;

    /**
     * This method is called explicity from projectsEvaluated.
     * There is no way for users to hook in before the build starts, this method is mostly used by internal listeners in Gradle
     * @see <a href="https://github.com/gradle/gradle/issues/4315">https://github.com/gradle/gradle/issues/4315</a>
     * @param gradle
     */
    @Override
    public void buildStarted(Gradle gradle) {
        checkNotNull(gradle);
        long now = clock.getCurrentTime();
        buildMetrics = new BuildMetrics(gradle.getStartParameter());
        buildMetrics.setBuildStarted(now);
        buildMetrics.setProfilingStarted(buildStartedTime.getStartTime());
    }

    @Override
    public void settingsEvaluated(Settings settings) {
        checkNotNull(settings);
        buildMetrics.setSettingsEvaluated(clock.getCurrentTime());
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
        checkNotNull(gradle);
        buildMetrics.setProjectsLoaded(clock.getCurrentTime());
    }

    // ProjectEvaluationListener
    @Override
    public void beforeEvaluate(Project project) {
        long now = clock.getCurrentTime();
        buildMetrics.getProjectProfile(project.getPath()).getConfigurationOperation().setStart(now);
    }

    @Override
    public void afterEvaluate(Project project, ProjectState state) {
        long now = clock.getCurrentTime();
        ProjectMetrics projectMetrics = buildMetrics.getProjectProfile(project.getPath());
        projectMetrics.getConfigurationOperation().setFinish(now);
    }

    // TaskExecutionListener
    @Override
    public void beforeExecute(Task task) {
        long now = clock.getCurrentTime();
        Project project = task.getProject();
        ProjectMetrics projectMetrics = buildMetrics.getProjectProfile(project.getPath());
        projectMetrics.getTaskProfile(task.getPath()).setStart(now);
    }

    @Override
    public void afterExecute(Task task, TaskState state) {
        long now = clock.getCurrentTime();
        Project project = task.getProject();
        ProjectMetrics projectMetrics = buildMetrics.getProjectProfile(project.getPath());
        TaskExecution taskExecution = projectMetrics.getTaskProfile(task.getPath());
        taskExecution.setFinish(now);
        taskExecution.completed(state);
    }

    @Override
    public void beforeResolve(ResolvableDependencies dependencies) {
        long now = clock.getCurrentTime();
        buildMetrics.getDependencySetProfile(dependencies.getPath()).setStart(now);
    }

    @Override
    public void afterResolve(ResolvableDependencies dependencies) {
        long now = clock.getCurrentTime();
        buildMetrics.getDependencySetProfile(dependencies.getPath()).setFinish(now);
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        checkNotNull(gradle);

        /*
         * There is no way for users to hook in before the build starts, this method is mostly used by internal listeners in Gradle
         * @see <a href="https://github.com/gradle/gradle/issues/4315">https://github.com/gradle/gradle/issues/4315</a>
         */
        buildStarted(gradle);

        buildMetrics.setProjectsEvaluated(clock.getCurrentTime());
        StartParameter startParameter = gradle.getStartParameter();
        checkState(!startParameter.isOffline(), "Collectors should not be registered when Gradle is running offline");
        try {
            dispatcherSupplier.get().startAsync().awaitRunning(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException | TimeoutException e) {
            logger.debug("Error while starting metrics dispatcher. Metrics collection disabled. Error message: {}", getRootCauseMessage(e));
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
            InfoBrokerPlugin plugin = getNebulaInfoBrokerPlugin(gradleProject);
            if (plugin == null) {
                dispatcher.environment(Info.create(tool));
            } else {
                GradleInfoCollector collector = new GradleInfoCollector(plugin);
                dispatcher.environment(Info.create(tool, collector.getSCM(), collector.getCI()));
            }
        } catch (Exception e) {
            logger.error("Unexpected exception in evaluation listener (error message: {})", getRootCauseMessage(e));
        }
    }

    private InfoBrokerPlugin getNebulaInfoBrokerPlugin(Project gradleProject) {
        Plugin plugin = gradleProject.getPlugins().findPlugin("nebula.info-broker");
        if (plugin == null) {
            plugin = gradleProject.getPlugins().findPlugin("info-broker");
        }
        return plugin != null ? (InfoBrokerPlugin) plugin : null;
    }

    /*
     * I have separated buildFinished from GradleCollector's BuildAdapter because we need it to be called
     * *after* all BuildListeners have completed their BuildFinished cycle. This is because InfoBrokerPlugin
     * only allows access to its reports after the BuildFinish cycle has completed.
     */
    public void buildFinishedClosure(BuildResult buildResult) {
        Throwable failure = buildResult.getFailure();
        Result result = failure == null ? Result.success() : Result.failure(failure);
        logger.info("Build finished with result " + result);
        MetricsDispatcher dispatcher = dispatcherSupplier.get();
        dispatcher.result(result);

        InfoBrokerPlugin infoBrokerPlugin = getNebulaInfoBrokerPlugin(buildResult.getGradle().getRootProject());
        if (infoBrokerPlugin != null) {
            Map<String, Object> reports = infoBrokerPlugin.buildReports();
            for (Map.Entry<String, Object> report : reports.entrySet()) {
                dispatcher.report(report.getKey(), report.getValue());
            }
        }

        buildResultComplete.getAndSet(true);
        shutdownIfComplete();
    }

    @Override
    public void buildFinished(BuildResult result) {
        buildMetrics.setBuildFinished(clock.getCurrentTime());
        buildFinished(buildMetrics);
        buildMetrics.setSuccessful(result.getFailure() == null);
        buildMetrics = null;
    }

    public void buildFinished(BuildMetrics result) {
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
        for (ProjectMetrics projectMetrics : result.getProjects()) {
            ContinuousOperation configurationOperation = projectMetrics.getConfigurationOperation();
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
        for (ProjectMetrics projectMetrics : result.getProjects()) {
            long totalTaskElapsed = 0;
            CompositeOperation<TaskExecution> tasks = projectMetrics.getTasks();
            for (TaskExecution execution : tasks.getOperations()) {
                Result taskResult = getTaskExecutionResult(execution);
                long taskElapsed = execution.getElapsedTime();
                nebula.plugin.metrics.model.Task task = new nebula.plugin.metrics.model.Task(execution.getDescription(), taskResult, new DateTime(execution.getStartTime()), taskElapsed);
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

        buildProfileComplete.getAndSet(true);
        shutdownIfComplete();
    }

    /**
     * Conditionally shutdown the dispatcher, because Gradle listener event order appears to be non-deterministic.
     */
    private void shutdownIfComplete() {
        // only shut down if you have updated build results AND profile information
        if (!buildProfileComplete.get() || !buildResultComplete.get()) {
            return;
        }

        MetricsDispatcher dispatcher = this.dispatcherSupplier.get();
        logger.info("Shutting down dispatcher");
        try {
            dispatcher.stopAsync().awaitTerminated(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.debug("Timed out after {}ms while waiting for metrics dispatcher to terminate", TIMEOUT_MS);
        } catch (IllegalStateException e) {
            logger.debug("Could not stop metrics dispatcher service (error message: {})", getRootCauseMessage(e));
        }

        Optional<String> receipt = dispatcher.receipt();
        if (receipt.isPresent()) {
            logger.warn(receipt.get());
        }
    }

    @VisibleForTesting
    Result getTaskExecutionResult(TaskExecution taskExecution) {
        Result result = Result.success();
        TaskState state = taskExecution.getState();
        if (state == null || !state.getDidWork()) {
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
