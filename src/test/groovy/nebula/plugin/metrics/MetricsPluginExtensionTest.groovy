package nebula.plugin.metrics

import org.gradle.api.logging.LogLevel
import org.joda.time.DateTime
import spock.lang.Shared
import spock.lang.Specification

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
}
