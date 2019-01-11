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
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Shared

/**
 * Integration tests for {@link MetricsPlugin}.
 */
@Slf4j
@Testcontainers
@IgnoreIf({ Boolean.valueOf(env["TRAVIS_BUILD_ID"]) }) //TODO: remove once we figure out the stability issues with Travis
class ESMetricsPluginIntegTest extends IntegrationSpec {

    private final ObjectMapper objectMapper = new ObjectMapper()

    @Shared
    ElasticsearchContainer container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:5.4.1")

    def setup() {
        while(!container.running) {
            log.info("waiting for container to be ready")
        }
    }

    def 'running projects task causes no errors and the build id to standard out'() {
        setup:
        createIndex()
        setValidBuildFile(DispatcherType.ES_HTTP)
        def result

        when:
        result = runTasksSuccessfully('projects')

        then:
        noExceptionThrown()
        result.standardError.isEmpty()
        result.standardOutput.contains('Build id is ')
    }

    def 'properties are sanitized'() {
        setup:
        createIndex()
        setValidBuildFile(DispatcherType.ES_HTTP)

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

        def buildId = getBuildIdAndIndex(runResult.standardOutput)

        def result = getBuild(buildId)
        def props = result._source.info.systemProperties
        props.find { it.key == propKey }?.value == 'SANITIZED'
    }

    def 'properties are sanitized via custom regex'() {
        setup:
        createIndex()
        setValidBuildFile(DispatcherType.ES_HTTP)

        def regex = "(?i).*\\\\_(ID)\\\$"
        buildFile << """
                     metrics {
                        sanitizedPropertiesRegex = "$regex"
                     }
                     """
        def runResult

        when:
        runResult = runTasksSuccessfully('-DMY_ID=myvalue1', '-Dsomething=value5', 'projects')

        then:
        runResult.standardError.isEmpty()

        def buildId = getBuildIdAndIndex(runResult.standardOutput)

        def result = getBuild(buildId)
        def props = result._source.info.systemProperties
        props.find { it.key == "MY_ID" }?.value == 'SANITIZED'
        props.find { it.key == "something" }?.value == 'value5'
    }


    def 'properties are sanitized via default regex'() {
        setup:
        createIndex()
        setValidBuildFile(DispatcherType.ES_HTTP)

        def runResult

        when:
        runResult = runTasksSuccessfully('-DMY_KEY=myvalue1', '-DMY_PASSWORD=myvalue2', '-DMY_SECRET=myvalue3', '-DMY_TOKEN=myvalue4', '-Dsomething=value5', 'projects')

        then:
        runResult.standardError.isEmpty()

        def buildId = getBuildIdAndIndex(runResult.standardOutput)

        def result = getBuild(buildId)
        def props = result._source.info.systemProperties
        props.find { it.key == "MY_KEY" }?.value == 'SANITIZED'
        props.find { it.key == "MY_PASSWORD" }?.value == 'SANITIZED'
        props.find { it.key == "MY_SECRET" }?.value == 'SANITIZED'
        props.find { it.key == "MY_TOKEN" }?.value == 'SANITIZED'
        props.find { it.key == "something" }?.value == 'value5'
    }

    def 'running offline results in no metrics being recorded'() {
        setup:
        createIndex()
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
        ${applyPlugin(MetricsPlugin)}

        metrics {
            esBasicAuthUsername = 'elastic'
            esBasicAuthPassword = 'changeme'
            httpPort = ${container.firstMappedPort}
            transportPort = ${container.tcpHost.port}
            clusterName = 'elasticsearch_mpit'
            dispatcherType = '$dispatcherType'
        }
    """.stripIndent()
        buildFile << build
    }

    private void createIndex() {
        try {
            callElastic('PUT', '/build-metrics-default')
        } catch (all) {
            log.info("Could not call create index - already exists")
        }
    }

    private callElastic(String method, String path) {
        def url = new URL("http://${container.httpHostAddress}${path}")
        def http = url.openConnection()
        http.setDoOutput(true)
        http.setRequestMethod(method)
        String userpass = "elastic:changeme"
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()))
        http.setRequestProperty("Authorization", basicAuth)
        http.setRequestProperty('User-agent', 'groovy script')
        return http.inputStream.getText("UTF-8")

    }

    private getBuildIdAndIndex(String output) {
        def m = output =~ /Build id is (.*)/
        def buildId = m[0][1] as String
        return buildId
    }

    private Map getBuild(String buildId) {
        def result = [:]
        while(result.isEmpty() || !result._source || !result._source.info) {
            def response = callElastic("GET", "/build-metrics-default/build/${buildId}")
            result = objectMapper.readValue(response, Map)
        }
        return result
    }
}
