/*
 *  Copyright 2015-2018 Netflix, Inc.
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

package nebula.plugin.metrics

import com.google.common.base.Optional
import nebula.plugin.metrics.dispatcher.AbstractMetricsDispatcher
import nebula.test.ProjectSpec
import org.gradle.api.GradleException
import org.gradle.api.internal.project.DefaultProject

class CustomMetricsDispatcherTest extends ProjectSpec {

    def 'should fail to evaluate project if CUSTOM dispatcher and not provided'() {
        setup:
        project.plugins.apply(MetricsPlugin)
        def metricsPluginExtension = project.extensions.findByName(MetricsPluginExtension.METRICS_EXTENSION_NAME)

        when:
        metricsPluginExtension.dispatcherType = MetricsPluginExtension.DispatcherType.CUSTOM

        and:
        def defaultProject = ((DefaultProject) project)
        defaultProject.getProjectEvaluationBroadcaster().afterEvaluate(project, project.getState())

        then:
        GradleException e = thrown(GradleException)
        e.message == 'setDispatcher should be called to set dispatcher when CUSTOM is selected as type'
    }

    def 'should evaluate project if CUSTOM dispatcher is provided'() {
        setup:
        project.plugins.apply(MetricsPlugin)
        def metricsPluginExtension = project.extensions.findByName(MetricsPluginExtension.METRICS_EXTENSION_NAME)
        def plugin = project.plugins.getPlugin(MetricsPlugin)
        MyCustomMetricsDispatcher myCustomMetricsDispatcher = new MyCustomMetricsDispatcher(metricsPluginExtension as MetricsPluginExtension)

        when:
        metricsPluginExtension.dispatcherType = MetricsPluginExtension.DispatcherType.CUSTOM
        plugin.setDispatcher(myCustomMetricsDispatcher)

        and:
        def defaultProject = ((DefaultProject) project)
        defaultProject.getProjectEvaluationBroadcaster().afterEvaluate(project, project.getState())

        then:
        notThrown(GradleException)
        plugin.dispatcher instanceof MyCustomMetricsDispatcher
    }


    private class MyCustomMetricsDispatcher extends AbstractMetricsDispatcher {

         MyCustomMetricsDispatcher(MetricsPluginExtension extension) {
            super(extension, false)
        }

        @Override
        protected String getCollectionName() {
            return null
        }

        @Override
        protected String index(String indexName, String type, String source, Optional<String> id) {
            return null
        }

        @Override
        protected void bulkIndex(String indexName, String type, Collection<String> sources) {

        }
    }
}
