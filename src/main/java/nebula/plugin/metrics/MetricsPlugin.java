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
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.invocation.DefaultGradle;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Gradle build metrics plugin.
 *
 * @author Danny Thomas
 */
public final class MetricsPlugin implements Plugin<Project> {
    private static final long SHUTDOWN_TIMEOUT_MS = 1000;
    private final Logger logger = MetricsLoggerFactory.getLogger(MetricsPlugin.class);
    private MetricsPluginExtension extension;
    private MetricsDispatcher dispatcher;

    @Override
    public void apply(Project project) {
        checkNotNull(project);
        checkState(project == project.getRootProject(), "The metrics plugin may only be applied to the root project");
        ExtensionContainer extensions = project.getExtensions();
        extensions.add("metrics", new MetricsPluginExtension());
        extension = extensions.getByType(MetricsPluginExtension.class);
        dispatcher = new ESClientMetricsDispatcher(extension);
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                configureCollectors(project);
            }
        });
    }

    @VisibleForTesting
    void setDispatcher(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    private void configureCollectors(Project project) {
        GradleInternal gradle = (DefaultGradle) project.getGradle();
        FilteringClassLoader filteringClassLoader = (FilteringClassLoader) gradle.getServices().get(ClassLoaderRegistry.class).getGradleApiClassLoader().getParent();
        filteringClassLoader.allowPackage("ch.qos.logback");

        LogbackCollector.configureLogbackCollection(dispatcher);

        gradle.addListener(new MetricsBuildListener(dispatcher));
        gradle.addListener(new GradleProfileCollector(dispatcher));

        GradleTestSuiteCollector suiteCollector = new GradleTestSuiteCollector(dispatcher);
        TaskContainer tasks = project.getTasks();
        for (String name : tasks.getNames()) {
            Task task = tasks.getByName(name);
            if (task instanceof Test) {
                ((Test) task).addTestListener(suiteCollector);
            }
        }
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
                try {
                    dispatcher.startAsync().awaitRunning();
                    dispatchProject(gradle);
                    GradleStartParameterCollector.collect(gradle.getStartParameter(), dispatcher);
                } catch (IllegalStateException e) {
                    logger.error("Error while starting metrics dispatcher. Metrics collection disabled.", e);
                }
            }
        }

        private void dispatchProject(Gradle gradle) {
            // TODO do we need to support sub-projects?
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
            if (dispatcher.isRunning()) {
                try {
                    dispatcher.stopAsync().awaitTerminated(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    logger.error("Timed out after {}ms while waiting for metrics dispatcher to terminate", SHUTDOWN_TIMEOUT_MS);
                } catch (IllegalStateException e) {
                    logger.error("Could not stop metrics dispatcher service", e);
                }
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
