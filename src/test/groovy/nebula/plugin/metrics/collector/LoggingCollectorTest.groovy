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

package nebula.plugin.metrics.collector

import com.google.common.base.Supplier
import nebula.plugin.metrics.MetricsLoggerFactory
import nebula.plugin.metrics.MetricsPluginExtension
import nebula.plugin.metrics.dispatcher.MetricsDispatcher
import nebula.test.ProjectSpec
import spock.lang.Ignore

/**
 * Tests for {@link LoggingCollector}.
 */
class LoggingCollectorTest extends ProjectSpec {
    def dispatcher = Mock(MetricsDispatcher)
    def extension = new MetricsPluginExtension()

    def setup() {
        extension.setLogLevel('info')
        LoggingCollector.configureCollection(new Supplier<MetricsDispatcher>() {
            @Override
            MetricsDispatcher get() {
                return dispatcher
            }
        }, extension)
    }

    def cleanup() {
        LoggingCollector.reset()
    }

    def 'events below threshold are not collected'() {
        when:
        project.logger.debug("debug")

        then:
        0 * dispatcher.logEvent(_)
    }

    @Ignore
    def 'events equal or greater than threshold are collected'() {
        def logger = project.logger

        when:
        logger.error("error")
        logger.warn("warn")
        logger.info("info") // this does not go through. Possibly because of
        // /src/core/org/gradle/logging/internal/OutputEventRenderer.java#238

        then:
        3 * dispatcher.logEvent(_)
    }

    @Ignore
    def 'events below threshold are collected, if they contain the metrics logging prefix'() {
        when:
        def logger = MetricsLoggerFactory.getLogger(LoggingCollectorTest)
        logger.debug("debug")

        then:
        1 * dispatcher.logEvent(_)
    }
}
