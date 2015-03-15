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

import nebula.plugin.metrics.collector.*;
import nebula.plugin.metrics.dispatcher.DispatcherLifecycleListener;
import nebula.plugin.metrics.dispatcher.ESClientMetricsDispatcher;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;

import com.google.common.annotations.VisibleForTesting;
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Gradle build metrics plugin.
 *
 * @author Danny Thomas
 */
public final class MetricsPlugin implements Plugin<Project> {
    private MetricsPluginExtension extension;
    private MetricsDispatcher dispatcher;

    @Override
    public void apply(Project project) {
        checkNotNull(project);
        checkState(project == project.getRootProject(), "The metrics plugin may only be applied to the root project");
        allowLogbackClassLoading(project);
        ExtensionContainer extensions = project.getExtensions();
        extensions.add("metrics", new MetricsPluginExtension());
        extension = extensions.getByType(MetricsPluginExtension.class);
        dispatcher = new ESClientMetricsDispatcher(extension);
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                configureCollectors(project);
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

    private void configureCollectors(Project project) {
        LogbackCollector.configureLogbackCollection(dispatcher);

        Gradle gradle = project.getGradle();
        gradle.addListener(new DispatcherLifecycleListener(dispatcher));
        gradle.addListener(new GradleBuildCollector(dispatcher));
        gradle.addListener(new GradleProfileCollector(dispatcher));

        GradleTestSuiteCollector suiteCollector = new GradleTestSuiteCollector(dispatcher);
        TaskContainer tasks = project.getTasks();
        for (String name : tasks.getNames()) {
            Task task = tasks.getByName(name);
            if (task instanceof Test) {
                ((Test) task).addTestListener(suiteCollector);
            }
        }
    }
}
