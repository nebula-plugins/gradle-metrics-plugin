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

import static com.github.tomakehurst.wiremock.client.WireMock.*

class RestMetricsIntegTest extends IntegrationSpec {

    @Rule
    WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicPort())

    def 'metrics posts data to preconfigured REST server'() {
        setup:
        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                restUri = 'http://localhost:${wireMockRule.port()}/myserver'
                dispatcherType = 'REST'
            }
        """.stripIndent()


        //First post
        stubFor(post('/myserver')
                .withRequestBody(containing("\"eventName\":\"build_metrics\""))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .willReturn(
                aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        //build is finished
        stubFor(post('/myserver')
                .withRequestBody(containing("\"eventName\":\"build_metrics\""))
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