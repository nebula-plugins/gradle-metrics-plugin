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

import nebula.plugin.metrics.collector.GradleBuildCollector;
import nebula.plugin.metrics.collector.GradleProfileCollector;
import nebula.plugin.metrics.collector.GradleTestSuiteCollector;
import nebula.plugin.metrics.collector.LogbackCollector;
import nebula.plugin.metrics.dispatcher.ESClientMetricsDispatcher;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
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

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Gradle build metrics plugin.
 *
 * @author Danny Thomas
 */
public final class MetricsPlugin implements Plugin<Project> {
    private MetricsDispatcher dispatcher;
    /**
     * Supplier allowing the dispatcher to be fetched lazily, so we can replace the instance for testing.
     */
    private Supplier<MetricsDispatcher> dispatcherSupplier = new Supplier<MetricsDispatcher>() {
        @Override
        public MetricsDispatcher get() {
            return checkNotNull(dispatcher, "Dispatcher has not yet been initialised");
        }
    };

    @Override
    public void apply(Project project) {
        checkNotNull(project);
        checkState(project == project.getRootProject(), "The metrics plugin may only be applied to the root project");
        allowLogbackClassLoading(project);
        ExtensionContainer extensions = project.getExtensions();
        extensions.add("metrics", new MetricsPluginExtension());
        MetricsPluginExtension extension = extensions.getByType(MetricsPluginExtension.class);
        dispatcher = new ESClientMetricsDispatcher(extension);
        configureRootProjectCollectors(project);
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                configureProjectCollectors(project.getAllprojects());
            }
        });
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

    private void configureRootProjectCollectors(Project rootProject) {
        Gradle gradle = rootProject.getGradle();
        LogbackCollector.configureLogbackCollection(dispatcherSupplier);
        // Listeners fire in the order they're registered, so you get FIFO evaluated/finished event ordering
        // Be mindful about how these are implemented and the registrations ordered
        gradle.addListener(new GradleProfileCollector(dispatcherSupplier));
        gradle.addListener(new GradleBuildCollector(dispatcherSupplier));
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
