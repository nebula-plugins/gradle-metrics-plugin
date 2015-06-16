package nebula.plugin.metrics

import ch.qos.logback.classic.Level
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
        extension.logLevel == Level.WARN
    }

    def 'setting log level is successful'() {
        when:
        extension.logLevel = 'info'

        then:
        extension.logLevel == Level.INFO
    }
}
