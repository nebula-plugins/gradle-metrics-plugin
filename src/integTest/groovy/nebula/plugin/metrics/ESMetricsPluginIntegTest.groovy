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

import com.google.common.io.Files
import nebula.plugin.info.InfoBrokerPlugin
import nebula.plugin.metrics.MetricsPluginExtension.DispatcherType
import nebula.test.IntegrationSpec
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Integration tests for {@link MetricsPlugin}.
 */
class ESMetricsPluginIntegTest extends IntegrationSpec {
    @Shared
    File dataDir

    @Shared
    Node node

    def setupSpec() {
        dataDir = Files.createTempDir()
        def settings = ImmutableSettings.settingsBuilder().put('path.data', dataDir).put('http.port', 19200).put('transport.tcp.port', 19300).put('cluster.name', 'elasticsearch_mpit').build()
        node = NodeBuilder.nodeBuilder().settings(settings).build()
        node.start()
    }

    def cleanup() {
        def admin = node.client().admin()
        def indices = admin.indices()
        indices.prepareDelete('_all').execute().actionGet()
    }

    boolean indexExists(String indexName) {
        def admin = node.client().admin()
        def indices = admin.indices()
        indices.prepareExists(indexName).execute().actionGet().isExists()
    }

    def cleanupSpec() {
        node.close()
        dataDir.deleteDir()
    }

    def getBuildIdAndIndex(String output) {
        def m = output =~ /Build id is (.*)/
        def buildId = m[0][1] as String

        def m2 = output =~ /(Creating|Using) index (.*) for metrics/
        String index = m2[0][2]

        return [buildId, index]
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
        dispatcherType << [DispatcherType.ES_CLIENT, DispatcherType.ES_HTTP]
    }

    @Unroll
    def "custom mapping file is used with #dispatcherType"() {
        setValidBuildFile(dispatcherType)
        File f = File.createTempFile('esmapping-', '')
        f.text = '''
        {
            "mappings": {
                "_default_": {
                    "_all": { "enabled": false },
                    "properties": {
                        "events": { "type": "nested" },
                        "tasks": { "type": "nested" },
                        "tests": { "type": "nested" },
                        "artifacts": { "type": "nested" },
                        "info": {
                            "properties": {
                                "environmentVariables": { "type": "nested" },
                                "systemProperties": { "type": "nested" }
                            }
                        },
                        "gradleLintViolations": { "type": "nested" },
                        "testingField": { "type": "nested" }
                    }
                }
            }
        }
        '''
        buildFile << "metrics.metricsIndexMappingFile = '${f.absolutePath}'"

        when:
        def result = runTasksSuccessfully('projects')

        then:
        noExceptionThrown()
        result.standardError.isEmpty()
        def (buildId, index) = getBuildIdAndIndex(result.standardOutput)

        def admin = node.client().admin()
        def indices = admin.indices()
        def mappings = indices.prepareGetMappings(index).get().mappings()
        mappings.get(index as String).get('build').source().string().contains('testingField')

        where:
        dispatcherType << [DispatcherType.ES_CLIENT, DispatcherType.ES_HTTP]
    }

    @Unroll('recorded build model is valid (#dispatcherType)')
    def 'recorded build model is valid'(DispatcherType dispatcherType) {
        setValidBuildFile(dispatcherType)
        def runResult

        when:
        runResult = runTasksSuccessfully('projects')

        then:
        runResult.standardError.isEmpty()

        def (buildId, index) = getBuildIdAndIndex(runResult.standardOutput)

        indexExists(index)

        def client = node.client()
        def result = client.prepareGet(index, 'build', buildId).execute().actionGet()
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
        dispatcherType << [DispatcherType.ES_CLIENT, DispatcherType.ES_HTTP]
    }

    @Unroll('properties are sanitized (#dispatcherType)')
    def 'properties are sanitized'(DispatcherType dispatcherType) {
        setValidBuildFile(dispatcherType)

        def propKey = 'java.version'
        buildFile << """
                     metrics {
                        sanitizedProperties = ['${propKey}']
                     }
                     """
        def runResult

        when:
        runResult = runTasksSuccessfully('projects')

        then:
        runResult.standardError.isEmpty()

        def (buildId, index) = getBuildIdAndIndex(runResult.standardOutput)

        indexExists(index as String)
        def client = node.client()
        def result = client.prepareGet(index, 'build', buildId).execute().actionGet()
        result.isExists()

        def props = result.source.info.systemProperties
        props.find { it.key == propKey }?.value == 'SANITIZED'

        where:
        dispatcherType << [DispatcherType.ES_CLIENT, DispatcherType.ES_HTTP]
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
        dispatcherType << [DispatcherType.ES_CLIENT, DispatcherType.ES_HTTP]
    }

    def setValidBuildFile(DispatcherType dispatcherType) {
        def build = """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                httpPort = 19200
                transportPort = 19300
                clusterName = 'elasticsearch_mpit'
                dispatcherType = '$dispatcherType'
            }
        """.stripIndent()
        buildFile << build
    }

    def 'report information is serialized correctly into elasticsearch'() {
        setValidBuildFile(dispatcherType)
        buildFile << """

        ${applyPlugin(InfoBrokerPlugin)}

        task createReport {
            doFirst {
                def broker = project.plugins.findPlugin(${InfoBrokerPlugin.name})
                broker.addReport('lintViolations', ['one', 'two', 'three'])
            }
        }

        """

        when:
        def runResult = runTasksSuccessfully('createReport')

        def (buildId, index) = getBuildIdAndIndex(runResult.standardOutput)

        def client = node.client()
        def metricsSent = client.prepareGet(index, 'build', buildId).execute().actionGet().source
        def lintViolationsReport = metricsSent['lintViolations']

        then:
        lintViolationsReport != null
        lintViolationsReport instanceof List
        (lintViolationsReport as List).equals(['one', 'two', 'three'])

        where:
        dispatcherType << [DispatcherType.ES_CLIENT, DispatcherType.ES_HTTP]
    }
}
