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


package nebula.plugin.metrics.dispatcher

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.node.TextNode
import com.github.tlrx.elasticsearch.test.EsSetup
import nebula.plugin.metrics.MetricsPluginExtension
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import spock.lang.Timeout

import static nebula.plugin.metrics.MetricsPluginExtension.DEFAULT_INDEX_NAME

/**
 * Integration tests for {@link ClientESMetricsDispatcher}.
 */
class ClientESMetricsDispatcherIntegTest extends LogbackAssertSpecification {
    EsSetup esSetup
    Client client
    ClientESMetricsDispatcher dispatcher

    def setup() {
        esSetup = new EsSetup()
        // Execute a dummy request so the ES client is initialised
        assert !esSetup.exists(DEFAULT_INDEX_NAME)
        client = esSetup.client()
        dispatcher = createStartedDispatcher(client)
    }

    def createStartedDispatcher(client) {
        def dispatcher = createDispatcher(new MetricsPluginExtension(), client)
        dispatcher.startAsync().awaitRunning()
        dispatcher
    }

    def createDispatcher(extension, client) {
        def dispatcher = new ClientESMetricsDispatcher(extension, client, false)
        dispatcher
    }

    def cleanup() {
        dispatcher.stopAsync().awaitTerminated()
        esSetup.terminate()
    }

    def 'starting the service results in index being created'() {
        expect:
        esSetup.exists(DEFAULT_INDEX_NAME)
    }

    @Timeout(value = 10)
    def 'transport client times out when node is not listening on configured port'() {
        super.cleanup() // Detach the logging asserter for this test
        def extension = new MetricsPluginExtension()
        def serverSocket = new ServerSocket(0)
        extension.transportPort = serverSocket.localPort
        serverSocket.close()
        def transportClient = createTransportClient(extension)
        def dispatcher = createDispatcher(extension, transportClient)

        when:
        dispatcher.startAsync().awaitRunning()

        then:
        thrown(IllegalStateException)
    }

    @Timeout(value = 10)
    def 'transport client times out when node is unresponsive on configured port'() {
        setup:
        super.cleanup() // Detach the logging asserter for this test
        def extension = new MetricsPluginExtension()
        def serverSocket = new ServerSocket(0)
        new Thread(new Runnable() {
            public void run() {
                serverSocket.accept()
            }
        }).start();
        extension.setTransportPort(serverSocket.localPort)
        def transportClient = createTransportClient(extension)
        def dispatcher = createDispatcher(extension, transportClient)

        when:
        dispatcher.startAsync().awaitRunning()

        then:
        thrown(IllegalStateException)

        cleanup:
        serverSocket.close()
    }

    def Client createTransportClient(MetricsPluginExtension extension) {
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder();
        builder.classLoader(Settings.class.getClassLoader());
        builder.put("cluster.name", extension.getClusterName());
        InetSocketTransportAddress address = new InetSocketTransportAddress(extension.getHostname(), extension.getTransportPort());
        new TransportClient(builder.build()).addTransportAddress(address);
    }

    def checkTypeIsNested(TreeNode treeNode) {
        TextNode type = treeNode.get('type') as TextNode
        assert type != null: "$treeNode  did not have a type"
        assert type.asText() == "nested": "Type was not 'nested', found '$type.asText'"
    }
}
