package nebula.plugin.metrics

import nebula.test.ProjectSpec
import org.elasticsearch.common.xcontent.XContentBuilder
import org.gradle.api.internal.project.AbstractProject
import org.gradle.listener.ListenerManager

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder

/*
 * Tests for {@link MetricsPlugin}.
 */
class MetricsPluginTest extends ProjectSpec {
    def 'applying plugin registers extension'() {
        when:
        project.plugins.apply(MetricsPlugin)

        then:
        project.extensions.findByName(MetricsExtension.METRICS_EXTENSION_NAME)
    }
}
