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

import com.google.common.io.Files
import nebula.test.IntegrationSpec
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder

/**
 * Integration tests for {@link MetricsPlugin}.
 */
class MetricsPluginIntegTest extends IntegrationSpec {
    def 'plugin applies'() {
        buildFile << """
            ${applyPlugin(MetricsPlugin)}
        """.stripIndent()

        when:
        runTasksSuccessfully('tasks')

        then:
        noExceptionThrown()
    }

    def 'running build results in metrics being recorded'(Node start) {
        def tempDir = Files.createTempDir()
        def settings = ImmutableSettings.settingsBuilder().put('path.data', tempDir).put('http.port', 19200).put('transport.tcp.port', 19300).build()
        def node = NodeBuilder.nodeBuilder().settings(settings).build()
        node.start()

        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                port = 19300
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('projects')

        then:
        noExceptionThrown()
        def indices = node.client().admin().indices()
        def exists = indices.prepareExists('build-metrics')
        exists.execute().actionGet().isExists()
        // TODO lots more assertions

        cleanup:
        node.close()
        tempDir.deleteDir()
    }
}
