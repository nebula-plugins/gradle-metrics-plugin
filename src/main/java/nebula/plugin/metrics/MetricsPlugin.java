package nebula.plugin.metrics;

import nebula.plugin.metrics.collector.GradleProfileCollector;
import nebula.plugin.metrics.collector.GradleStartParameterCollector;
import nebula.plugin.metrics.collector.GradleTestSuiteCollector;
import nebula.plugin.metrics.collector.LogbackCollector;
import nebula.plugin.metrics.dispatcher.ESClientMetricsDispatcher;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Result;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.invocation.DefaultGradle;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Gradle build metrics plugin.
 *
 * @author Danny Thomas
 */
public final class MetricsPlugin implements Plugin<Project> {
    private static final long SHUTDOWN_TIMEOUT_MS = 1000;
    private final Logger logger = MetricsLoggerFactory.getLogger(MetricsPlugin.class);

    @Override
    public void apply(Project project) {
        checkNotNull(project);
        configureExtension(project);
        configureCollectors(project);
    }

    private void configureExtension(Project project) {
        project.getExtensions().add("metrics", new MetricsExtension());
    }

    private void configureCollectors(Project project) {
        GradleInternal gradle = (DefaultGradle) project.getGradle();
        FilteringClassLoader filteringClassLoader = (FilteringClassLoader) gradle.getServices().get(ClassLoaderRegistry.class).getGradleApiClassLoader().getParent();
        filteringClassLoader.allowPackage("ch.qos.logback");

        MetricsExtension extension = MetricsExtension.getRootMetricsExtension(gradle);
        MetricsDispatcher dispatcher = new ESClientMetricsDispatcher(extension);

        LogbackCollector.configureLogbackCollection(dispatcher);

        gradle.addListener(new MetricsBuildListener(dispatcher));
        gradle.addListener(new GradleProfileCollector(dispatcher));

        final GradleTestSuiteCollector suiteCollector = new GradleTestSuiteCollector(dispatcher);
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                TaskContainer tasks = project.getTasks();
                for (String name : tasks.getNames()) {
                    Task task = tasks.getByName(name);
                    if (task instanceof Test) {
                        ((Test) task).addTestListener(suiteCollector);
                    }
                }
            }
        });
    }

    @VisibleForTesting
    class MetricsBuildListener implements BuildListener {
        private final MetricsDispatcher dispatcher;

        MetricsBuildListener(MetricsDispatcher dispatcher) {
            this.dispatcher = checkNotNull(dispatcher);
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            StartParameter startParameter = gradle.getStartParameter();
            if (startParameter.isOffline()) {
                logger.warn("Build is running offline. Metrics will not be collected");
            } else {
                dispatcher.startAsync().awaitRunning();
            }
            dispatchProject(gradle);
            GradleStartParameterCollector.collect(gradle.getStartParameter(), dispatcher);
        }

        private void dispatchProject(Gradle gradle) {
            Project gradleProject = gradle.getRootProject();
            String name = gradleProject.getName();
            String version = String.valueOf(gradleProject.getVersion());
            nebula.plugin.metrics.model.Project project = nebula.plugin.metrics.model.Project.create(name, version);
            dispatcher.started(project);
        }

        @Override
        public void buildFinished(BuildResult buildResult) {
            Throwable failure = buildResult.getFailure();
            Result result = failure == null ? Result.success() : Result.failure(failure);
            dispatcher.result(result);
            try {
                if (dispatcher.isRunning()) {
                    dispatcher.stopAsync().awaitTerminated(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
            } catch (TimeoutException e) {
                logger.error("Timed out after {}ms while waiting for metrics dispatcher to terminate", SHUTDOWN_TIMEOUT_MS);
            }
        }

        @Override
        public void buildStarted(Gradle gradle) {
            // We register this listener too late to catch this event, so we use projectsEvaluated instead
        }

        @Override
        public void settingsEvaluated(Settings settings) {
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
        }
    }
}
