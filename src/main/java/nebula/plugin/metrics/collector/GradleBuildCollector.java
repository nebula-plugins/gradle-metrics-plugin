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

import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Info;
import nebula.plugin.metrics.model.Result;

import com.google.common.base.Supplier;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A collector for {@link BuildListener} events that collects project and start parameter data.
 *
 * @author Danny Thomas
 */
public final class GradleBuildCollector implements BuildListener {
    private final Logger logger = MetricsLoggerFactory.getLogger(GradleBuildCollector.class);
    private final Supplier<MetricsDispatcher> dispatcherSupplier;

    public GradleBuildCollector(Supplier<MetricsDispatcher> dispatcherSupplier) {
        this.dispatcherSupplier = checkNotNull(dispatcherSupplier);
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        checkNotNull(gradle);
        StartParameter startParameter = gradle.getStartParameter();
        if (startParameter.isOffline()) {
            logger.warn("Build is running offline. Metrics will not be collected");
            return;
        } else {
            try {
                dispatcherSupplier.get().startAsync().awaitRunning();
            } catch (IllegalStateException e) {
                logger.error("Error while starting metrics dispatcher. Metrics collection disabled.", e);
                return;
            }
        }

        try {
            Project gradleProject = gradle.getRootProject();
            String name = gradleProject.getName();
            String version = String.valueOf(gradleProject.getVersion());
            nebula.plugin.metrics.model.Project project = new nebula.plugin.metrics.model.Project(name, version);
            MetricsDispatcher dispatcher = dispatcherSupplier.get();
            dispatcher.started(project); // We register this listener after the build has started, so we fire the start event here instead

            nebula.plugin.metrics.model.Gradle tool = new nebula.plugin.metrics.model.Gradle(gradle.getStartParameter());
            Plugin plugin = gradleProject.getPlugins().findPlugin("info-broker");
            if (plugin == null) {
                logger.info("Gradle info plugin not found. SCM and CI information will not be collected");
                dispatcher.environment(Info.create(tool));
            } else {
                GradleInfoCollector collector = new GradleInfoCollector(plugin);
                dispatcher.environment(Info.create(tool, collector.getSCM(), collector.getCI()));
            }
        } catch (Exception e) {
            logger.error("Unexpected exception in evaluation listener", e);
        }
    }

    @Override
    public void buildFinished(BuildResult buildResult) {
        Throwable failure = buildResult.getFailure();
        Result result = failure == null ? Result.success() : Result.failure(failure);
        dispatcherSupplier.get().result(result);
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
