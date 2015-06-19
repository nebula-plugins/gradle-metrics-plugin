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

import nebula.plugin.metrics.collector.GradleCollector;
import nebula.plugin.metrics.collector.GradleTestSuiteCollector;
import nebula.plugin.metrics.collector.LogbackCollector;
import nebula.plugin.metrics.dispatcher.ClientESMetricsDispatcher;
import nebula.plugin.metrics.dispatcher.HttpESMetricsDispatcher;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.dispatcher.UninitializedMetricsDispatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.invocation.DefaultGradle;
import org.slf4j.Logger;

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

    @Override
    public void apply(Project project) {
        checkNotNull(project);
        checkState(project == project.getRootProject(), "The metrics plugin may only be applied to the root project");
        allowLogbackClassLoading(project);
        ExtensionContainer extensions = project.getExtensions();
        extensions.add("metrics", new MetricsPluginExtension());
        Gradle gradle = project.getGradle();
        StartParameter startParameter = gradle.getStartParameter();
        if (startParameter.isOffline()) {
            logger.warn("Build is running offline. Metrics will not be collected");
        } else {
            final MetricsPluginExtension extension = extensions.getByType(MetricsPluginExtension.class);
            configureRootProjectCollectors(project, extension);
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
                        }
                    }
                    configureProjectCollectors(project.getAllprojects());
                }
            });
        }
    }

    @VisibleForTesting
    void setDispatcher(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    private void allowLogbackClassLoading(Project project) {
        GradleInternal gradle = (DefaultGradle) project.getGradle();
        ClassLoaderRegistry registry = gradle.getServices().get(ClassLoaderRegistry.class);
        FilteringClassLoader classLoader = (FilteringClassLoader) registry.getGradleApiClassLoader().getParent();
        classLoader.allowPackage("ch.qos.logback");
    }

    private void configureRootProjectCollectors(Project rootProject, MetricsPluginExtension extension) {
        Gradle gradle = rootProject.getGradle();
        LogbackCollector.configureLogbackCollection(dispatcherSupplier, extension);
        gradle.addListener(new GradleCollector(dispatcherSupplier));
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
