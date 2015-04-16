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

package nebula.plugin.metrics

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.LoggingEvent
import nebula.plugin.metrics.dispatcher.MetricsDispatcher
import nebula.test.ProjectSpec
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.invocation.DefaultGradle
import org.gradle.listener.ListenerBroadcast

/*
 * Tests for {@link MetricsPlugin}.
 * </p>
 * I'm not really concerned with mocking out Gradle here: it's a first class collaborator, and there's no benefit
 * in guessing at it's behaviour, or avoiding real interactions.
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

    def 'build lifecycle events control dispatcher lifecycle'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)
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
        def dispatcher = applyPluginWithMockedDispatcher(project)
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

    def 'project evaluation dispatches result event'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)

        when:
        buildListenerBroadcaster(project).buildFinished(new BuildResult(project.gradle, null))

        then:
        1 * dispatcher.result(_)
    }

    def 'project logger dispatches logback event'() {
        def dispatcher = applyPluginWithMockedDispatcher(project)

        when:
        project.logger.info('log message')

        then:
        1 * dispatcher.logbackEvent(_) >> { LoggingEvent event ->
            assert event.message == 'log message'
            assert event.level == Level.INFO
        }
    }

    def 'afterTest notification dispatches test event'() {
        project.plugins.apply(JavaPlugin)
        def dispatcher = applyPluginWithMockedDispatcher(project)
        def task = project.tasks.getByName('test') as Test

        // This is pretty coupley, but it means we can use the same infrastructure as Gradle to trigger the result (I'll use test execution in integration tests)
        def method = Test.getDeclaredMethod('getTestListenerBroadcaster')
        method.setAccessible(true)
        def listener = method.invoke(task) as ListenerBroadcast<TestListener>
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
