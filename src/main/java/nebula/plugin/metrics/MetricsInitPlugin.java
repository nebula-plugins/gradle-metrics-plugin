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

import nebula.plugin.metrics.model.BuildMetrics;

import org.gradle.api.invocation.BuildInvocationDetails;
import org.gradle.api.invocation.Gradle;

import javax.inject.Inject;

public class MetricsInitPlugin extends AbstractMetricsPlugin<Gradle> {

    @Inject
    public MetricsInitPlugin(BuildInvocationDetails buildInvocationDetails) {
        super(buildInvocationDetails);
    }

    @Override
    public void apply(Gradle gradle) {
        if(isOfflineMode(gradle)) {
           gradle.rootProject(project -> {
               createMetricsExtension(project);
               project.getLogger().warn("Build is running offline. Metrics will not be collected.");
           });
            return;
        }
        BuildMetrics buildMetrics = initializeBuildMetrics(gradle);
        createAndRegisterGradleBuildMetricsCollector(gradle, buildMetrics);
        gradle.rootProject(this::configureProject);
    }
}
