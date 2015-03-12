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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.LoggingEvent
import nebula.test.ProjectSpec
import org.gradle.api.Project
import org.slf4j.LoggerFactory

/**
 * Tests for {@link nebula.plugin.metrics.AssertAppender}.
 */
class AssertAppenderTest extends ProjectSpec {
    def 'appending an error level event results in a AssertionError'() {
        given:
        def appender = new AssertAppender()
        def event = new LoggingEvent()
        event.setLevel(Level.ERROR)

        when:
        appender.append(event)

        then:
        thrown(AssertionError)
    }

    def 'appending an warning level event does not result in a AssertionError'() {
        given:
        def appender = new AssertAppender()
        def event = new LoggingEvent()
        event.setLevel(Level.WARN)

        when:
        appender.append(event)

        then:
        notThrown(AssertionError)
    }

    def 'logging and error level event for a Gradle project results in a AssertionError'() {
        given:
        def appender = new AssertAppender()
        appender.start()

        def projectLogger = project.getLogger()
        def factoryLogger = (Logger) LoggerFactory.getLogger(Project)
        factoryLogger.addAppender(appender)

        when:
        projectLogger.error("Error message")

        then:
        thrown(AssertionError)

        cleanup:
        appender.stop()
    }
}
