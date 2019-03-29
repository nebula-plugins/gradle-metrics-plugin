/*
 * Copyright 2015-2019 Netflix, Inc.
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

package nebula.plugin.metrics.dispatcher

import com.sun.net.httpserver.BasicAuthenticator
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import nebula.plugin.metrics.MetricsPluginExtension
import spock.lang.Specification
import spock.lang.Unroll

class HttpESMetricsDispatcherTest extends Specification {

    private final static String user = 'user'
    private final static String pass = 'pass'
    private final static String context = '/'
    private final static int esPort = 1337

    @Unroll
    def "HttpESMetricsDispatcher should use basic auth if configured: #basicAuth"(boolean basicAuth) {
        given: 'running mock ES instance and HttpESMetricsDispatcher, both using basic auth if configured'
        HttpServer mockEsInstance = createMockEsInstance(basicAuth)
        mockEsInstance.start()
        MetricsPluginExtension metrics = new MetricsPluginExtension()
        metrics.setHostname('localhost')
        metrics.setHttpPort(esPort)
        metrics.setIndexName('index')
        if (basicAuth) {
            metrics.setEsBasicAuthUsername(user)
            metrics.setEsBasicAuthPassword(pass)
        }
        HttpESMetricsDispatcher dispatcher = new HttpESMetricsDispatcher(metrics)
        dispatcher.startAsync().awaitRunning()

        when: 'dispatcher tries to create an index'
        def indexExists = dispatcher.exists('index')

        then: 'request should succeed, and we should see a valid response'
        noExceptionThrown()
        indexExists

        cleanup:
        mockEsInstance?.stop(0)

        where: basicAuth << [true, false]
    }

    @Unroll
    def "HttpESMetricsDispatcher should use full uri if configured: #fullUriPresent"(boolean fullUriPresent) {
        given: 'running mock ES instance and HttpESMetricsDispatcher'
        def port = fullUriPresent ? 1338 : esPort
        HttpServer mockEsInstance = createMockEsInstance(false, port)
        mockEsInstance.start()
        MetricsPluginExtension metrics = new MetricsPluginExtension()
        metrics.setHostname('localhost')
        metrics.setHttpPort(esPort)
        metrics.setIndexName('index')
        if (fullUriPresent) {
            metrics.setFullURI("http://localhost:$port")
        }
        HttpESMetricsDispatcher dispatcher = new HttpESMetricsDispatcher(metrics)
        dispatcher.startAsync().awaitRunning()

        when: 'dispatcher tries to create an index'
        def indexExists = dispatcher.exists('index')

        then: 'request should succeed, and we should see a valid response'
        noExceptionThrown()
        indexExists

        cleanup:
        mockEsInstance?.stop(0)

        where: fullUriPresent << [true, false]
    }

    private static HttpServer createMockEsInstance(boolean basicAuth, int port = esPort) {
        HttpServer mockEsInstance = HttpServer.create(new InetSocketAddress(port), 0)
        HttpContext rootCtx = mockEsInstance.createContext(context, buildEsHttpHandler())
        if (basicAuth) {
            rootCtx.setAuthenticator(new BasicAuthenticator(context) {
                @Override
                boolean checkCredentials(String username, String password) {
                    username == user && password == pass
                }
            })
        }
        mockEsInstance
    }

    private static HttpHandler buildEsHttpHandler() {
        new HttpHandler() {
            @Override
            void handle(HttpExchange t) throws IOException {
                def response = "{\"found\": true}"
                t.sendResponseHeaders(200, response.length())
                OutputStream os = t.getResponseBody()
                os.write(response.getBytes())
                os.close()
            }
        }
    }
}
