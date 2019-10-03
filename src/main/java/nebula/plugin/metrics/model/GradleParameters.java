/*
 *  Copyright 2015-2019 Netflix, Inc.
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

package nebula.plugin.metrics.model;

import lombok.Value;
import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Gradle start parameters.
 */
@Value
public class GradleParameters {
    public static GradleParameters fromGradle(org.gradle.api.invocation.Gradle gradle) {
        StartParameter start = gradle.getStartParameter();
        List<KeyValue> projectProperties = KeyValue.mapToKeyValueList(start.getProjectProperties());
        return new GradleParameters(start.getTaskRequests(),
                start.getExcludedTaskNames(),
                start.isBuildProjectDependencies(),
                start.getCurrentDir(),
                start.getProjectDir(),
                projectProperties,
                start.getGradleUserHomeDir(),
                start.getSettingsFile(),
                start.getBuildFile(),
                start.getInitScripts(),
                start.isDryRun(),
                start.isRerunTasks(),
                start.isProfile(),
                start.isContinueOnFailure(),
                start.isOffline(),
                start.getProjectCacheDir(),
                start.isRefreshDependencies(),
                start.getMaxWorkerCount(),
                start.isConfigureOnDemand()
        );
    }

    private List<TaskExecutionRequest> taskRequests;

    private Set<String> excludedTaskNames;

    private boolean buildProjectDependencies;

    private File currentDir;

    private File projectDir;

    private List<KeyValue> projectProperties;

    private File gradleUserHomeDir;

    private File settingsFile;

    private File buildFile;

    private List<File> initScripts;

    private boolean dryRun;

    private boolean rerunTasks;

    private boolean profile;

    private boolean continueOnFailure;

    private boolean offline;

    private File projectCacheDir;

    private boolean refreshDependencies;

    private int maxWorkerCount;

    private boolean configureOnDemand;
}
