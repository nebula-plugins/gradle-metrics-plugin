/*
 * Copyright 2015 the original author or authors.
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

package nebula.plugin.metrics

import nebula.plugin.metrics.dispatcher.MetricsDispatcher
import nebula.test.ProjectSpec
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaPlugin
import org.gradle.invocation.DefaultGradle

/*
 * Tests for {@link MetricsPlugin}.
 */

class MetricsPluginTest extends ProjectSpec {
    def 'applying plugin registers extension'() {
        when:
        project.plugins.apply(MetricsPlugin)

        then:
        project.extensions.findByName(MetricsPluginExtension.METRICS_EXTENSION_NAME)
    }

    def 'plugin can only be applied to root project'() {
        given:
        def subproject = addSubproject('subproject')

        when:
        subproject.plugins.apply(MetricsPlugin)

        then:
        thrown(IllegalStateException)
    }

    def 'project evaluation succeeds'() {
        // Ideally, I'd check that all of the listeners we expect have been registered here, but unfortunately Gradle gives us no way at getting at those
        when:
        project.plugins.apply(MetricsPlugin)
        project.evaluate()

        then:
        noExceptionThrown()
    }

    def 'build lifecycle events control dispatcher lifecycle'() {
        def dispatcher = pluginWithMockedDispatcher(project)
        1 * dispatcher.startAsync() >> {
            dispatcher.isRunning() >> true
            dispatcher
        }
        1 * dispatcher.stopAsync() >> dispatcher

        DefaultGradle gradle = project.gradle

        when:
        def broadcaster = buildListenerBroadcaster(project)
        broadcaster.projectsEvaluated(gradle)
        broadcaster.buildFinished(new BuildResult(gradle, null))

        then:
        noExceptionThrown()
    }

    def 'running build offline causes dispatcher not to be started'() {
        def dispatcher = pluginWithMockedDispatcher(project)
        def broadcaster = buildListenerBroadcaster(project)
        def gradle = Mock(Gradle)
        1 * gradle.getStartParameter() >> {
            def parameter = Mock(StartParameter)
            parameter.isOffline() >> true
            parameter
        }

        when:
        broadcaster.projectsEvaluated(gradle)

        then:
        0 * dispatcher.startAsync()
    }

    def 'test suite listeners are registered when plugin is applied to java project'() {
        project.plugins.apply(JavaPlugin)
        def dispatcher = pluginWithMockedDispatcher(project)

        expect:
        true // FIXME actually assert something
    }

    BuildListener buildListenerBroadcaster(Project project) {
        DefaultGradle gradle = project.gradle
        gradle.buildListenerBroadcaster
    }

    MetricsDispatcher pluginWithMockedDispatcher(Project project) {
        project.plugins.apply(MetricsPlugin)
        def plugin = project.plugins.getPlugin(MetricsPlugin)
        MetricsDispatcher dispatcher = Mock()
        plugin.setDispatcher(dispatcher)
        ((DefaultProject) project).evaluate()
        dispatcher
    }
}
