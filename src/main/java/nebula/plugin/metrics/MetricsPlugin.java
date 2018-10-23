/*
 *  Copyright 2015-2016 Netflix, Inc.
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

package nebula.plugin.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import groovy.lang.Closure;
import nebula.plugin.metrics.collector.GradleBuildMetricsCollector;
import nebula.plugin.metrics.collector.GradleTestSuiteCollector;
import nebula.plugin.metrics.dispatcher.*;
import nebula.plugin.metrics.time.BuildStartedTime;
import nebula.plugin.metrics.time.Clock;
import nebula.plugin.metrics.time.MonotonicClock;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.scan.time.BuildScanBuildStartedTime;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Gradle build metrics plugin.
 *
 * @author Danny Thomas
 */
public final class MetricsPlugin implements Plugin<Project> {
    private final Logger logger = MetricsLoggerFactory.getLogger(MetricsPlugin.class);
    private final Clock clock = new MonotonicClock();
    private MetricsDispatcher dispatcher = new UninitializedMetricsDispatcher();
    /**
     * Supplier allowing the dispatcher to be fetched lazily, so we can replace the instance for testing.
     */
    private Supplier<MetricsDispatcher> dispatcherSupplier = new Supplier<MetricsDispatcher>() {
        @Override
        public MetricsDispatcher get() {
            return dispatcher;
        }
    };

    private final BuildScanBuildStartedTime buildScanBuildStartedTime;

    @Inject
    public MetricsPlugin(BuildScanBuildStartedTime buildScanBuildStartedTime) {
        this.buildScanBuildStartedTime = buildScanBuildStartedTime;
    }

    @Override
    public void apply(Project project) {
        checkNotNull(project);

        //Using internal API to retrieve build start time but still storing it in our own data structure
        BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(buildScanBuildStartedTime.getBuildStartedTime());

        checkState(project == project.getRootProject(), "The metrics plugin may only be applied to the root project");
        ExtensionContainer extensions = project.getExtensions();
        extensions.add("metrics", new MetricsPluginExtension());
        Gradle gradle = project.getGradle();
        StartParameter startParameter = gradle.getStartParameter();
        final MetricsPluginExtension extension = extensions.getByType(MetricsPluginExtension.class);

        if (project.hasProperty("metrics.enabled") && "false".equals(project.property("metrics.enabled"))) {
            logger.warn("Metrics have been disabled for this build.");
            return;
        }

        if (startParameter.isOffline()) {
            logger.warn("Build is running offline. Metrics will not be collected.");
            return;
        }

        configureRootProjectCollectors(project, buildStartedTime);
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                if (dispatcher instanceof UninitializedMetricsDispatcher) {
                    switch (extension.getDispatcherType()) {
                        case ES_CLIENT: {
                            dispatcher = new ClientESMetricsDispatcher(extension);
                            break;
                        }
                        case ES_HTTP: {
                            dispatcher = new HttpESMetricsDispatcher(extension);
                            break;
                        }
                        case SPLUNK: {
                            dispatcher = new SplunkMetricsDispatcher(extension);
                            break;
                        }
                        case REST: {
                            dispatcher = new RestMetricsDispatcher(extension);
                            break;
                        }
                        case NOOP: {
                            dispatcher = new NoopMetricsDispatcher(extension);
                            break;
                        }
                    }
                }
                configureProjectCollectors(project.getAllprojects());
            }
        });

    }

    @VisibleForTesting
    void setDispatcher(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    private void configureRootProjectCollectors(Project rootProject, BuildStartedTime buildStartedTime) {
        final Gradle gradle = rootProject.getGradle();
        final GradleBuildMetricsCollector gradleCollector = new GradleBuildMetricsCollector(dispatcherSupplier, buildStartedTime, clock);
        gradle.addListener(gradleCollector);
        gradle.buildFinished(new Closure(null) {
            protected Object doCall(Object arguments) {
                gradleCollector.buildFinishedClosure((BuildResult)arguments);
                return null;
            }
        });
    }

    private void configureProjectCollectors(Set<Project> projects) {
        for (Project project : projects) {
            TaskContainer tasks = project.getTasks();
            for (String name : tasks.getNames()) {
                Task task = tasks.getByName(name);
                if (task instanceof Test) {
                    GradleTestSuiteCollector suiteCollector = new GradleTestSuiteCollector(dispatcherSupplier, (Test) task);
                    ((Test) task).addTestListener(suiteCollector);
                }
            }
        }
    }
}
