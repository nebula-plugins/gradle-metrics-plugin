/*
 * Copyright 2013-2015 the original author or authors.
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

package nebula.plugin.metrics.dispatcher

import ch.qos.logback.classic.Logger
import ch.qos.logback.core.Appender
import nebula.plugin.metrics.AssertAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * A Spock {@link Specification} that ensures that Logback messages error level or higher are not logged, by adding an
 * {@link AssertAppender}.
 */
class LogbackAssertSpecification extends Specification {
    private Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    private Appender appender = new AssertAppender()

    def setup() {
        rootLogger.addAppender(appender)
        appender.start()
    }

    def cleanup() {
        rootLogger.detachAppender(appender)
    }

}
