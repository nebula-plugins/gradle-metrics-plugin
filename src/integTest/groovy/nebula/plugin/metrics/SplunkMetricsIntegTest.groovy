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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import nebula.test.IntegrationSpec
import org.junit.Rule

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.containing
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor

class SplunkMetricsIntegTest extends IntegrationSpec {

    @Rule
    WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicPort())

    def 'metrics posts data to Mocked Splunk Universal forwarder with Basic Authorization Header'() {
        setup:
        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                dispatcherType = 'SPLUNK'

                splunkInputType = 'FORWARDER'
                splunkUri = 'http://localhost:${wireMockRule.port()}/myserver'
                headers['Authorization'] = 'Basic YWRtaW46Y2hhbmdlbWU='
            }
        """.stripIndent()

        stubFor(post('/myserver')
                .withHeader("Authorization", equalTo("Basic YWRtaW46Y2hhbmdlbWU="))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("project\":{\"name\":\"${moduleName}\",\"version\":\"unspecified\"}"))
                .willReturn(
                aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        expect:
        runTasksSuccessfully('projects')


    }

    def 'metrics posts data to Mocked Splunk HTTP Collector with Splunk Authorization Header'() {
        setup:
        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                dispatcherType = 'SPLUNK'

                splunkInputType = 'HTTP_COLLECTOR'
                splunkUri = 'http://localhost:${wireMockRule.port()}/myserver'
                headers['Authorization'] = 'Splunk 8BA5A780-6B3A-472D-BF2F-CF4E9FFF4E9D'
            }
        """.stripIndent()

        stubFor(post('/myserver')
                .withHeader("Authorization", equalTo("Splunk 8BA5A780-6B3A-472D-BF2F-CF4E9FFF4E9D"))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("project\":{\"name\":\"${moduleName}\",\"version\":\"unspecified\"}"))
                .willReturn(
                aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        expect:
        runTasksSuccessfully('projects')

    }
}
