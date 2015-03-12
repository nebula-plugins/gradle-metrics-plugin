package nebula.plugin.metrics

import nebula.test.ProjectSpec

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
}
