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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import nebula.test.IntegrationSpec
import org.apache.commons.io.IOUtils

import static nebula.plugin.metrics.dispatcher.AbstractMetricsDispatcher.getObjectMapper
import static nebula.plugin.metrics.dispatcher.RestMetricsDispatcher.RestPayload

class RestMetricsIntegTest extends IntegrationSpec {

    String lastReportedBuildMetricsEvent

    class RestMockHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            if (t.getRequestMethod() == 'POST') {
                def body = IOUtils.toString(t.getRequestBody())
                if (body.contains('"eventName":"build_metrics"')) {
                    lastReportedBuildMetricsEvent = body
                }
            } else {
                throw new IllegalStateException('Metrics plugin should only POST data')
            }
            def response = 'OK'
            t.sendResponseHeaders(200, response.length())
            OutputStream os = t.getResponseBody()
            os.write(response.getBytes())
            os.close()
        }

    }

    def 'metrics posts data to preconfigured REST server'() {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/", new RestMockHandler())
        server.setExecutor(null)
        server.start()

        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                restUri = 'http://localhost:${server.address.port}'
                dispatcherType = 'REST'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('projects')

        then:
        RestPayload restPayload = getObjectMapper().readValue(lastReportedBuildMetricsEvent, RestPayload.class)
        def buildJson = new JsonSlurper().parseText(restPayload.payload.get('build'))

        restPayload.eventName == 'build_metrics'
        buildJson.with {
            project.name == moduleName
            project.version == 'unspecified'
            startTime
            finishedTime
            elapsedTime
            result.status == 'success'
            !events.isEmpty()
            tasks.size() == 1
            tests.isEmpty()
        }

        cleanup:
        server?.stop(0)
    }

}