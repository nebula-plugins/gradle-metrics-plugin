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

package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.MetricsLoggerFactory;

import com.google.common.base.Supplier;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link BuildListener} that controls the {@link MetricsDispatcher} lifecycle.
 *
 * @author Danny Thomas
 */
public final class DispatcherLifecycleListener implements BuildListener {
    private static final Logger logger = MetricsLoggerFactory.getLogger(DispatcherLifecycleListener.class);

    private final Supplier<MetricsDispatcher> dispatcherSupplier;

    public DispatcherLifecycleListener(Supplier<MetricsDispatcher> dispatcherSupplier) {
        this.dispatcherSupplier = checkNotNull(dispatcherSupplier);
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        StartParameter startParameter = gradle.getStartParameter();
        if (startParameter.isOffline()) {
            logger.warn("Build is running offline. Metrics will not be collected");
        } else {
            try {
                dispatcherSupplier.get().startAsync().awaitRunning();
            } catch (IllegalStateException e) {
                logger.debug("Error while starting metrics dispatcher", e); // The failure should bubble up separately, so we only log with an stacktrace at debug level
                logger.error("Error while starting metrics dispatcher. Metrics collection disabled.");
            }
        }
    }

    @Override
    public void buildFinished(BuildResult buildResult) {
        // We can't shutdown the dispatcher here, because the BuildProfile events are dispatched after buildFinished
        checkNotNull(buildResult);
    }

    @Override
    public void buildStarted(Gradle gradle) {
        // We register this listener too late to catch this event, so we use projectsEvaluated instead
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
