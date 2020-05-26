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

import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.BuildInvocationDetails;
import org.gradle.api.invocation.Gradle;

import javax.inject.Inject;

public class MetricsSettingsPlugin extends AbstractMetricsPlugin<Settings> {

    @Inject
    public MetricsSettingsPlugin(BuildInvocationDetails buildInvocationDetails) {
        super(buildInvocationDetails);
    }

    @Override
    public void apply(Settings settings) {
        Gradle gradle = settings.getGradle();
        applyToGradle(gradle);
    }
}
