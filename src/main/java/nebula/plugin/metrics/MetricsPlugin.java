package nebula.plugin.metrics;

import nebula.plugin.metrics.collector.GradleProfileCollector;
import nebula.plugin.metrics.collector.GradleStartParameterCollector;
import nebula.plugin.metrics.collector.GradleTestSuiteCollector;
import nebula.plugin.metrics.collector.LogbackAppenderCollector;
import nebula.plugin.metrics.dispatcher.ESClientMetricsDispatcher;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.CI;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.SCM;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
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
    private final Logger logger = LoggerFactory.getLogger(MetricsPlugin.class);

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
        Gradle gradle = project.getGradle();

        MetricsExtension extension = MetricsExtension.getRootMetricsExtension(gradle);
        MetricsDispatcher dispatcher = new ESClientMetricsDispatcher(extension);

        // FIXME this is obviously a total hack, but it's the easiest way of getting past the classloading filtering, fix this!
        //FilteringClassLoader filteringClassLoader = (FilteringClassLoader) getClass().getClassLoader().getParent().getParent();
        //filteringClassLoader.allowPackage("ch.qos.logback");
        //LogbackAppenderCollector.addLogbackAppender(dispatcher, Project.class);

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
                logger.warn("[metrics] Build is running offline. Metrics will not be collected");
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
                logger.error("Timed after {}ms while waiting for metrics dispatcher to terminate", SHUTDOWN_TIMEOUT_MS);
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
