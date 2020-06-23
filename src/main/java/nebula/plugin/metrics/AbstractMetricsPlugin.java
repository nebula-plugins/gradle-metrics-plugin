/*
 *  Copyright 2020 Netflix, Inc.
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
import nebula.plugin.metrics.model.BuildMetrics;
import nebula.plugin.metrics.time.BuildStartedTime;
import nebula.plugin.metrics.time.Clock;
import nebula.plugin.metrics.time.MonotonicClock;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.BuildInvocationDetails;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractMetricsPlugin<T> implements Plugin<T> {

    public static String METRICS_ENABLED_PROPERTY = "metrics.enabled";
    private MetricsDispatcher dispatcher = new UninitializedMetricsDispatcher();
    private final Clock clock = new MonotonicClock();
    private final BuildInvocationDetails buildInvocationDetails;

    /**
     * Supplier allowing the dispatcher to be fetched lazily, so we can replace the instance for testing.
     */
    private final Supplier<MetricsDispatcher> dispatcherSupplier = () -> dispatcher;

    private final Action<Project> configureProjectCollectorAction = p -> p.getTasks().withType(Test.class).configureEach(test -> {
        GradleTestSuiteCollector suiteCollector = new GradleTestSuiteCollector(dispatcherSupplier, test);
        test.addTestListener(suiteCollector);
    });

    @Inject
    public AbstractMetricsPlugin(BuildInvocationDetails buildInvocationDetails) {
        this.buildInvocationDetails = buildInvocationDetails;
    }

    public void applyToGradle(Gradle gradle) {
        if(isOfflineMode(gradle)) {
            gradle.rootProject(project -> {
                createMetricsExtension(project);
                project.getLogger().warn("Build is running offline. Metrics will not be collected.");
            });
            return;
        }
        if(isMetricsDisabled(gradle)) {
            gradle.rootProject(project -> {
                createMetricsExtension(project);
                project.getLogger().warn("Metrics have been disabled for this build.");
            });
            return;
        }
        BuildMetrics buildMetrics = initializeBuildMetrics(gradle);
        createAndRegisterGradleBuildMetricsCollector(gradle, buildMetrics);
        gradle.rootProject(this::configureProject);
    }

    protected BuildMetrics initializeBuildMetrics(Gradle gradle) {
        BuildMetrics buildMetrics = new BuildMetrics(gradle.getStartParameter());
        BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(buildInvocationDetails.getBuildStartedTime());
        buildMetrics.setBuildStarted(buildStartedTime.getStartTime());
        buildMetrics.setProfilingStarted(buildStartedTime.getStartTime());
        return buildMetrics;
    }

    protected void createAndRegisterGradleBuildMetricsCollector(Gradle gradle, BuildMetrics buildMetrics) {
        //Using internal API to retrieve build start time but still storing it in our own data structure
        BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(buildInvocationDetails.getBuildStartedTime());
        final GradleBuildMetricsCollector gradleCollector = new GradleBuildMetricsCollector(dispatcherSupplier, buildStartedTime, gradle, buildMetrics, clock);
        gradle.addListener(gradleCollector);
        gradle.buildFinished(new Closure(null) {
            protected Object doCall(Object arguments) {
                gradleCollector.buildFinishedClosure((BuildResult)arguments);
                return null;
            }
        });
    }

    protected boolean isOfflineMode(Gradle gradle) {
        return gradle.getStartParameter().isOffline();
    }

    protected boolean isMetricsDisabled(Gradle gradle) {
        String metricsEnabledParameter = gradle.getStartParameter().getProjectProperties().get(METRICS_ENABLED_PROPERTY);
        boolean metricsDisabled = metricsEnabledParameter != null && metricsEnabledParameter.equals("false");
        if(metricsDisabled) {
            return true;
        }
        File gradleProperties = new File(gradle.getStartParameter().getProjectDir(), "gradle.properties");
        if(gradleProperties.exists()) {
            try (Stream<String> stream = Files.lines(gradleProperties.toPath())) {
                return stream.anyMatch(line ->
                        line.contains(METRICS_ENABLED_PROPERTY) && line.contains("false"));
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    protected MetricsPluginExtension createMetricsExtension(Project project) {
        return project.getExtensions().create("metrics", MetricsPluginExtension.class);
    }

    protected void configureProject(Project project) {
        checkNotNull(project);
        checkState(project == project.getRootProject(), "The metrics plugin may only be applied to the root project");

        final MetricsPluginExtension extension = createMetricsExtension(project);

        if (project.hasProperty(METRICS_ENABLED_PROPERTY) && "false".equals(project.property(METRICS_ENABLED_PROPERTY))) {
            dispatcher = new NoopMetricsDispatcher(extension);
            project.getLogger().warn("Metrics have been disabled for this build.");
            return;
        }

        project.afterEvaluate(gradleProject -> {
            if (dispatcher instanceof UninitializedMetricsDispatcher) {
                switch (extension.getDispatcherType()) {
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
                    case CUSTOM: {
                        if(dispatcher instanceof UninitializedMetricsDispatcher) {
                            throw new GradleException("setDispatcher should be called to set dispatcher when CUSTOM is selected as type");
                        }
                        break;
                    }
                }
            }
            configureProjectCollectors(gradleProject);
        });
    }

    public void setDispatcher(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    @VisibleForTesting
    MetricsDispatcher getDispatcher() {
        return dispatcher;
    }

    private void configureProjectCollectors(Project project) {
        configureProjectCollectorAction.execute(project);
        for (Project subproject : project.getSubprojects()) {
            // perform task scan for subprojects after subproject evaluation
            subproject.afterEvaluate(configureProjectCollectorAction);
        }
    }

}
