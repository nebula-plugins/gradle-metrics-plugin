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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.github.tlrx.elasticsearch.test.EsSetup
import nebula.plugin.metrics.MetricsPluginExtension
import nebula.plugin.metrics.model.*
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.gradle.StartParameter
import spock.lang.Timeout

import static junit.framework.Assert.assertFalse
import static nebula.plugin.metrics.dispatcher.ESClientMetricsDispatcher.*

/**
 * Integration tests for {@link ESClientMetricsDispatcher}.
 */
class ESClientMetricsDispatcherIntegTest extends LogbackAssertSpecification {
    EsSetup esSetup
    Client client
    ESClientMetricsDispatcher dispatcher

    def setup() {
        esSetup = new EsSetup()
        // Execute a dummy request so the ES client is initialised
        assertFalse(esSetup.exists(BUILD_METRICS_INDEX))
        client = esSetup.client()
        dispatcher = createStartedDispatcher(client)
    }

    def createStartedDispatcher(client) {
        def dispatcher = createDispatcher(new MetricsPluginExtension(), client)
        dispatcher.startAsync().awaitRunning()
        dispatcher
    }

    def createDispatcher(extension, client) {
        def dispatcher = new ESClientMetricsDispatcher(extension, client, false)
        dispatcher
    }

    def cleanup() {
        dispatcher.stopAsync().awaitTerminated()
        esSetup.terminate()
    }

    def 'starting the service results in index and mappings being created'() {
        expect:
        esSetup.exists(BUILD_METRICS_INDEX)
    }

    def 'nested mappings are configured correctly'() {
        when:
        dispatcher.started(new Project('', ''))
        dispatcher.event('description', 'type', 0)
        Gradle tool = new Gradle(new StartParameter());
        SCM scm = new UnknownSCM()
        CI ci = new UnknownCI()
        dispatcher.environment(Environment.create(tool, scm, ci));
        dispatcher.result(Result.success())

        then:
        def indices = client.admin().indices()
        def response = indices.prepareGetMappings(BUILD_METRICS_INDEX).setTypes(BUILD_TYPE).get()
        def source = response.mappings.get(BUILD_METRICS_INDEX).get(BUILD_TYPE).source().string()

        // The ES apis don't seem to let us get at the type definitions for nested types via sourceAsMap, so we'll just parse as a tree ourselves
        def mapper = new ObjectMapper()
        def jp = mapper.getFactory().createParser(source)
        def root = mapper.readTree(jp)
        def properties = root.get('build').get('properties')
        NESTED_MAPPINGS.each { type, children ->
            if (children.isEmpty()) {
                checkTypeIsNested(properties.get(type))
            } else {
                def parent = properties.get(type).get('properties')
                children.each { child ->
                    checkTypeIsNested(parent.get(child))
                }
            }
        }
    }

    @Timeout(value = 10)
    def 'transport client times out when node is not listening on configured port'() {
        super.cleanup() // Detach the logging asserter for this test
        def extension = new MetricsPluginExtension()
        def serverSocket = new ServerSocket(0)
        extension.port = serverSocket.localPort
        serverSocket.close()
        def transportClient = createTransportClient(extension)
        def dispatcher = createDispatcher(extension, transportClient)

        when:
        dispatcher.startAsync().awaitRunning()

        then:
        thrown(IllegalStateException)
    }

    @Timeout(value = 10)
    def 'transport client times out when node is unresponsible on configured port'() {
        setup:
        super.cleanup() // Detach the logging asserter for this test
        def extension = new MetricsPluginExtension()
        def serverSocket = new ServerSocket(0)
        new Thread(new Runnable() {
            public void run() {
                serverSocket.accept()
            }
        }).start();
        extension.setPort(serverSocket.localPort)
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
        InetSocketTransportAddress address = new InetSocketTransportAddress(extension.getHostname(), extension.getPort());
        new TransportClient(builder.build()).addTransportAddress(address);
    }

    def checkTypeIsNested(TreeNode treeNode) {
        TextNode type = treeNode.get('type')
        assert type != null: "$treeNode  did not have a type"
        assert type.asText() == "nested": "Type was not 'nested', found '$type.asText'"
    }
}
