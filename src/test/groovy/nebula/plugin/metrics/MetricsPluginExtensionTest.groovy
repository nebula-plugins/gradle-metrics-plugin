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

import org.gradle.api.logging.LogLevel
import org.joda.time.DateTime
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for {@link MetricsPluginExtension}.
 */
class MetricsPluginExtensionTest extends Specification {
    @Shared
    def extension = new MetricsPluginExtension()

    def 'default log level is warning'() {
        expect:
        extension.logLevel == LogLevel.WARN
    }

    def 'setting log level is successful'() {
        when:
        extension.logLevel = 'info'

        then:
        extension.logLevel == LogLevel.INFO
    }

    def 'logstash rolling formats as expected'() {
        expect:
        DateTime yearMonth = MetricsPluginExtension.ROLLING_FORMATTER.parseDateTime('201501')
        MetricsPluginExtension.ROLLING_FORMATTER.print(yearMonth) == '201501'
    }

    def 'logstash index name is rolling'() {
        expect:
        def pattern = MetricsPluginExtension.ROLLING_FORMATTER.print(DateTime.now())
        extension.logstashIndexName == 'logstash-build-metrics-default-' + pattern
    }

    @Unroll
    def "metrics index name is #expectedName when rolling index is #rollingIndex"() {
        setup:
        extension.setRollingIndex(rollingIndex)

        expect:
        extension.indexName == expectedName

        where:
        rollingIndex || expectedName
        true         || "build-metrics-default-${MetricsPluginExtension.ROLLING_FORMATTER.print(DateTime.now())}"
        false        || 'build-metrics-default'


    }
}
