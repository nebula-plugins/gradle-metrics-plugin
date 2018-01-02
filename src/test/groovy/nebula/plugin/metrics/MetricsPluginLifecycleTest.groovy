/*
 *  Copyright 2015-2016 Netflix, Inc.
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

import nebula.plugin.metrics.dispatcher.MetricsDispatcher
import nebula.test.ProjectSpec
import org.gradle.BuildListener
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.invocation.DefaultGradle

class MetricsPluginLifecycleTest extends ProjectSpec {
    def 'applying plugin registers extension'() {
        when:
        project.plugins.apply(MetricsPlugin)

        then:
        project.extensions.findByName(MetricsPluginExtension.METRICS_EXTENSION_NAME)
    }

    def 'project evaluation dispatches started event'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)
        1 * dispatcher.startAsync() >> dispatcher

        when:
        buildListenerBroadcaster(project).projectsEvaluated(project.gradle)

        then:
        1 * dispatcher.started(_)
    }

    def 'project evaluation dispatches environment event'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)
        1 * dispatcher.startAsync() >> dispatcher

        when:
        buildListenerBroadcaster(project).projectsEvaluated(project.gradle)

        then:
        1 * dispatcher.environment(_)
    }

    def 'afterTest notification dispatches test event'() {
        project.plugins.apply(JavaPlugin)
        def dispatcher = applyPluginWithMockedDispatcher(project)
        def task = project.tasks.getByName('test') as Test

        // This is pretty coupley, but it means we can use the same infrastructure as Gradle to trigger the result (I'll use test execution in integration tests)
        def field = Test.superclass.getDeclaredField('testListenerBroadcaster')
        field.setAccessible(true)
        def listener = field.get(task) as ListenerBroadcast<TestListener>
        def testListener = listener.getSource()

        def descriptor = Mock(TestDescriptor)
        descriptor.getName() >> 'name'
        descriptor.getClassName() >> 'className'
        def result = Mock(TestResult)
        result.getResultType() >> TestResult.ResultType.SUCCESS

        nebula.plugin.metrics.model.Test capturedTest = null

        when:
        testListener.afterTest(descriptor, result)

        then:
        1 * dispatcher.test(_) >> {
            capturedTest = it.get(0)
        }
        capturedTest.getMethodName() == 'name'
        capturedTest.getClassName() == 'className'
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
