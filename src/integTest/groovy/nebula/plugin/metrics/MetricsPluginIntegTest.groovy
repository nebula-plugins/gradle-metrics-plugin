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
import nebula.plugin.metrics.MetricsPluginExtension.DispatcherType
import nebula.test.IntegrationSpec
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Integration tests for {@link MetricsPlugin}.
 */
class MetricsPluginIntegTest extends IntegrationSpec {
    @Shared
    File dataDir

    @Shared
    Node node

    @Shared
    Map<DispatcherType, Integer> dispatcherPorts;

    def setupSpec() {
        dataDir = Files.createTempDir()
        dispatcherPorts = [:]
        dispatcherPorts.put(DispatcherType.ES_HTTP, 19200)
        dispatcherPorts.put(DispatcherType.ES_CLIENT, 19300)
        def settings = ImmutableSettings.settingsBuilder().put('path.data', dataDir).put('http.port', 19200).put('transport.tcp.port', 19300).put('cluster.name', 'elasticsearch_mpit').build()
        node = NodeBuilder.nodeBuilder().settings(settings).build()
        node.start()
    }

    def cleanup() {
        if (indexExists()) {
            def admin = node.client().admin()
            def indices = admin.indices()
            indices.prepareDelete('build-metrics').execute().actionGet()
        }
    }

    boolean indexExists() {
        def admin = node.client().admin()
        def indices = admin.indices()
        indices.prepareExists('build-metrics').execute().actionGet().isExists()
    }

    def cleanupSpec() {
        node.close()
        dataDir.deleteDir()
    }

    @Unroll('running projects task causes no errors and the build id to standard out (#dispatcherType)')
    def 'running projects task causes no errors and the build id to standard out'(DispatcherType dispatcherType) {
        setValidBuildFile(dispatcherType)
        def result

        when:
        result = runTasksSuccessfully('projects')

        then:
        noExceptionThrown()
        result.standardError.isEmpty()
        result.standardOutput.contains('Build id is ')

        where:
        dispatcherType << DispatcherType.values()
    }

    @Unroll('recorded build model is valid (#dispatcherType)')
    def 'recorded build model is valid'(DispatcherType dispatcherType) {
        setValidBuildFile(dispatcherType)
        def runResult

        when:
        runResult = runTasksSuccessfully('projects')

        then:
        indexExists()

        def m = runResult.standardOutput =~ /Build id is (.*)/
        def buildId = m[0][1] as String
        def client = node.client()
        def result = client.prepareGet('build-metrics', 'build', buildId).execute().actionGet()
        result.isExists()

        def source = result.source
        def project = source.project
        project.name == moduleName
        project.version == 'unspecified'
        source.startTime
        source.finishedTime
        source.elapsedTime
        source.result.status == 'success'
        !source.events.isEmpty()
        source.tasks.size() == 1
        source.tests.isEmpty()

        where:
        dispatcherType << DispatcherType.values()
    }

    @Unroll('running offline results in no metrics being recorded (#dispatcherType)')
    def 'running offline results in no metrics being recorded'(DispatcherType dispatcherType) {
        setValidBuildFile(dispatcherType)
        def result

        when:
        result = runTasksSuccessfully("--offline", "projects")

        then:
        noExceptionThrown()
        result.standardOutput.contains("Build is running offline")

        where:
        dispatcherType << DispatcherType.values()
    }

    def setValidBuildFile(DispatcherType dispatcherType) {
        def build = """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                port = ${dispatcherPorts.get(dispatcherType)}
                clusterName = 'elasticsearch_mpit'
                dispatcherType = '$dispatcherType'
            }
        """.stripIndent()
        buildFile << build
    }
}
