/*
 *  Copyright 2015-2019 Netflix, Inc.
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

import groovy.util.logging.Slf4j
import nebula.plugin.metrics.MetricsPluginExtension.DispatcherType
import nebula.test.IntegrationSpec
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Shared

/**
 * Integration tests for {@link MetricsPlugin}.
 */
@Slf4j
@Testcontainers
@IgnoreIf({ Boolean.valueOf(env["NEBULA_IGNORE_TEST"]) })
class ESMetricsPluginIntegTest extends IntegrationSpec {


    @Shared
    ElasticsearchContainer container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:5.4.1")

    def 'running projects task causes no errors and the build id to standard out'() {
        setup:
        setValidBuildFile(DispatcherType.ES_HTTP)
        def result

        when:
        result = runTasksSuccessfully('projects')

        then:
        noExceptionThrown()
        result.standardError.isEmpty()
        result.standardOutput.contains('Build id is ')
        getBuildId(result.standardOutput)
    }

    def 'running offline results in no metrics being recorded'() {
        setup:
        setValidBuildFile(DispatcherType.ES_HTTP)
        def result

        when:
        result = runTasksSuccessfully("--offline", "projects")

        then:
        noExceptionThrown()
        result.standardOutput.contains("Build is running offline")
    }


    def setValidBuildFile(DispatcherType dispatcherType) {
        def build = """

        apply plugin: 'java'
        apply plugin: "nebula.info"
        ${applyPlugin(MetricsPlugin)}

        metrics {
            esBasicAuthUsername = 'elastic'
            esBasicAuthPassword = 'changeme'
            httpPort = ${container.firstMappedPort}
            transportPort = ${container.tcpHost.port}
            clusterName = 'elasticsearch_mpit'
            dispatcherType = '$dispatcherType'
        }
        
        repositories {
            mavenCentral()
        }
        
        dependencies {
            implementation 'com.google.guava:guava:19.0'
        }
    """.stripIndent()
        buildFile << build
    }

    private getBuildId(String output) {
        def m = output =~ /Build id is (.*)/
        def buildId = m[0][1] as String
        return buildId
    }
}
