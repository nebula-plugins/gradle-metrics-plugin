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

package nebula.plugin.metrics

import com.google.common.base.Optional
import com.google.common.util.concurrent.Service
import nebula.plugin.metrics.dispatcher.MetricsDispatcher
import nebula.test.ProjectSpec
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.api.internal.project.DefaultProject
import org.gradle.invocation.DefaultGradle

class MetricsPluginTest extends ProjectSpec {

    def mockService = Mock(Service)

    def 'plugin can only be applied to root project'() {
        given:
        def subproject = addSubproject('subproject')

        when:
        subproject.plugins.apply(MetricsPlugin)

        then:
        thrown(PluginApplicationException)
    }

    def 'build lifecycle events control dispatcher startup lifecycle'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)
        1 * dispatcher.startAsync() >> {
            dispatcher.isRunning() >> true
            dispatcher
        }
        1 * dispatcher.stopAsync() >> mockService
        1 * dispatcher.receipt() >> Optional.of("")

        DefaultGradle gradle = project.gradle

        when:
        def broadcaster = buildListenerBroadcaster(project)
        broadcaster.projectsEvaluated(gradle)
        broadcaster.buildFinished(new BuildResult(gradle, null))

        then:
        noExceptionThrown()
    }

    def 'project evaluation dispatches result event'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)
        def buildResult = new BuildResult(project.gradle, null)

        when:
        buildListenerBroadcaster(project).buildFinished(buildResult)

        then:
        1 * dispatcher.result(_)
        1 * dispatcher.stopAsync() >> mockService
        1 * dispatcher.receipt() >> Optional.of("")
    }

    BuildListener buildListenerBroadcaster(Project project) {
        def gradle = project.gradle as DefaultGradle
        gradle.buildListenerBroadcaster
    }

    MetricsDispatcher applyPluginWithMockedDispatcher(Project project) {
        project.plugins.apply(MetricsPlugin)
        def plugin = project.plugins.getPlugin(MetricsPlugin)
        def dispatcher = Mock(MetricsDispatcher)
        plugin.setDispatcher(dispatcher)
        def defaultProject = ((DefaultProject) project)
        defaultProject.getProjectEvaluationBroadcaster().afterEvaluate(project, project.getState())
        dispatcher
    }
}
