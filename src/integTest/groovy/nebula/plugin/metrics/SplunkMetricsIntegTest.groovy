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
import org.apache.http.StatusLine

import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType

import org.apache.log4j.Logger

class SplunkMetricsIntegTest extends IntegrationSpec {

    final static Logger logger = Logger.getLogger(SplunkMetricsIntegTest.class);

    String lastReportedBuildMetricsEvent
    private def slurper = new JsonSlurper()

    class SplunkEndPointMock implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            
            def response = 'OK'
            if (t.getRequestMethod() == 'POST') {

                def splunkHeader = 'Basic YWRtaW46Y2hhbmdlbWU='
                def authHeader = t.getRequestHeaders()['Authorization'][0]

                if (authHeader == splunkHeader){ 
                    lastReportedBuildMetricsEvent = IOUtils.toString(t.getRequestBody())
                    
                    t.sendResponseHeaders(200, response.length())
                } else {
                    response = 'Unauthorized'
                    t.sendResponseHeaders(401, response.length())
                }
                OutputStream os = t.getResponseBody()
                os.write(response.getBytes())
                os.close()
            }
        }
    }

    def 'metrics posts data to Mocked Splunk Universal forwarder with Basic Authorization Header'() {
        HttpServer server = HttpServer.create(new InetSocketAddress(1337), 0)
        server.createContext("/", new SplunkEndPointMock())
        server.setExecutor(null)
        server.start()

        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                dispatcherType = 'SPLUNK'

                splunkInputType = 'FORWARDER'
                splunkUri = 'http://localhost:1337/'
                splunkAuthHeader = 'Basic YWRtaW46Y2hhbmdlbWU='
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('projects')

        then:
        def buildJson = slurper.parseText(lastReportedBuildMetricsEvent)

        buildJson.with {
            buildInfo.project.name == moduleName
            buildInfo.project.version == 'unspecified'
            buildInfo.startTime
            buildInfo.finishedTime
            buildInfo.elapsedTime
            buildInfo.result.status == 'success'
            !buildInfo.events.isEmpty()
            buildInfo.tasks.size() == 1
            buildInfo.tests.isEmpty()
        }

        cleanup:
        server?.stop(0)
    }
}
