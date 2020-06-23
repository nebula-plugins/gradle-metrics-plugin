/*
 *  Copyright 2015-2020 Netflix, Inc.
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

import nebula.plugin.metrics.dispatcher.NoopMetricsDispatcher;
import nebula.plugin.metrics.model.BuildMetrics;
import org.gradle.api.Project;

import org.gradle.api.invocation.BuildInvocationDetails;
import javax.inject.Inject;

/**
 * Gradle build metrics plugin.
 *
 * @author Danny Thomas
 */
public final class MetricsPlugin extends AbstractMetricsPlugin<Project> {

    @Inject
    public MetricsPlugin(BuildInvocationDetails buildInvocationDetails) {
        super(buildInvocationDetails);
    }

    @Override
    public void apply(Project project) {
        if(isOfflineMode(project.getGradle())) {
            createMetricsExtension(project);
            project.getLogger().warn("Build is running offline. Metrics will not be collected.");
            return;
        }
        if (project.hasProperty(METRICS_ENABLED_PROPERTY) && "false".equals(project.property(METRICS_ENABLED_PROPERTY))) {
            createMetricsExtension(project);
            project.getLogger().warn("Metrics have been disabled for this build.");
            return;
        }
        BuildMetrics buildMetrics = initializeBuildMetrics(project.getGradle());
        createAndRegisterGradleBuildMetricsCollector(project.getGradle(), buildMetrics);
        configureProject(project);
    }
}
