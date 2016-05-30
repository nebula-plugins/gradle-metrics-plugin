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

package nebula.plugin.metrics.dispatcher
import nebula.plugin.metrics.SimpleOutputEventListener
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext
import org.slf4j.LoggerFactory
import spock.lang.Specification
/**
 * A Spock {@link Specification} that ensures that logging messages error level or higher are not logged.
 */
abstract class LoggingCaptureSpecification extends Specification {
    protected SimpleOutputEventListener listener = new SimpleOutputEventListener();

    def setup() {
        def factory = LoggerFactory.getILoggerFactory() as OutputEventListenerBackedLoggerContext
        factory.setOutputEventListener(listener)
    }

    public void noErrorsLogged() {
        assert listener.getLogEvents().findAll { it.logLevel == LogLevel.ERROR }.isEmpty()
    }
}
