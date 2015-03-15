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

package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Result;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A collector for {@link BuildListener} events that collects project and start parameter data.
 *
 * @author Danny Thomas
 */
public final class GradleBuildCollector implements BuildListener {
    private final MetricsDispatcher dispatcher;

    public GradleBuildCollector(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        Project gradleProject = gradle.getRootProject();
        String name = gradleProject.getName();
        String version = String.valueOf(gradleProject.getVersion());
        nebula.plugin.metrics.model.Project project = nebula.plugin.metrics.model.Project.create(name, version);
        dispatcher.started(project);
        GradleStartParameterCollector.collect(gradle.getStartParameter(), dispatcher);
    }

    @Override
    public void buildFinished(BuildResult buildResult) {
        Throwable failure = buildResult.getFailure();
        Result result = failure == null ? Result.success() : Result.failure(failure);
        dispatcher.result(result);
    }

    @Override
    public void buildStarted(Gradle gradle) {
        checkNotNull(gradle);
    }

    @Override
    public void settingsEvaluated(Settings settings) {
        checkNotNull(settings);
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
        checkNotNull(gradle);
    }
}
