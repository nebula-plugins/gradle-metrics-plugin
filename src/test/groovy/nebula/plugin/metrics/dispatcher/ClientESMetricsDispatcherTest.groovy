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

import nebula.plugin.metrics.MetricsPluginExtension
import nebula.plugin.metrics.model.Project
import org.elasticsearch.action.ListenableActionFuture
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.index.IndexRequestBuilder
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.AdminClient
import org.elasticsearch.client.Client
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.common.joda.time.format.DateTimeFormatter
import org.elasticsearch.common.xcontent.XContentBuilder
import org.joda.time.DateTime

import static ClientESMetricsDispatcher.BUILD_TYPE

/**
 * Tests for {@link ClientESMetricsDispatcher}.
 */
class ClientESMetricsDispatcherTest extends LoggingCaptureSpecification {
    def extension = new MetricsPluginExtension()
    def project = new Project("project", "1.0")

    ClientESMetricsDispatcher dispatcher

    def 'build receipt is output'() {
        def builder = mockIndexRequestBuilder()
        dispatcher = createStartedDispatcher(builder)

        when:
        dispatcher.started(project)
        dispatcher.stopAsync().awaitTerminated()

        then:
        def receipt = dispatcher.receipt().get()
        receipt.startsWith('You can find the metrics for this build at http://localhost:9200/build-metrics-default/build/')
        receipt.split('/')[-1] ==~ /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
        noErrorsLogged()
    }

    def 'build start event contains the expected json representation'() {
        def builder = mockIndexRequestBuilder()
        dispatcher = createStartedDispatcher(builder)
        def json = null as String

        when:
        dispatcher.started(project)

        then:
        1 * builder.setSource(_) >> { String source ->
            json = source.replace('\n', '')
            builder
        }
        // TODO Ignoring the tail because it includes timestamps which I'm being too lazy to match right now
        json.startsWith('{"project":{"name":"project","version":"1.0"},"events":[],"tasks":[],"tests":[],"result":{"status":"unknown"}')
        json.contains('"eventsCount":0')
        json.contains('"eventsElapsedTime":0')

        when:
        dispatcher.stopAsync().awaitTerminated()

        then:
        noExceptionThrown()
        noErrorsLogged()
    }

    def 'mapper formats dates using the same format as content builder'() {
        DateTimeFormatter datePrinter = XContentBuilder.defaultDatePrinter
        def mapper = ClientESMetricsDispatcher.getObjectMapper()

        expect:
        def dateTime = new DateTime()
        def printedDate = datePrinter.print(dateTime.millis)
        def mappedDate = mapper.writeValueAsString(dateTime)
        printedDate == mappedDate.replace('"', '')
    }

    def ClientESMetricsDispatcher createStartedDispatcher() {
        def builder = mockIndexRequestBuilder()
        createStartedDispatcher(builder)
    }

    def ClientESMetricsDispatcher createStartedDispatcher(IndexRequestBuilder builder) {
        def client = mockClient(builder)
        def dispatcher = new ClientESMetricsDispatcher(extension, client, false)
        dispatcher.startAsync().awaitRunning()
        dispatcher
    }

    def Client mockClient(IndexRequestBuilder builder) {
        def client = Mock(Client)
        client.prepareIndex(_, _) >> builder
        def admin = Mock(AdminClient)
        def index = Mock(IndicesAdminClient)
        client.admin() >> admin
        admin.indices() >> index
        index.prepareExists(_) >> mockIndiciesExistsBuilder()
        client
    }

    def IndexRequestBuilder mockIndexRequestBuilder() {
        def builder = Mock(IndexRequestBuilder)
        def future = Mock(ListenableActionFuture)
        def response = new IndexResponse(MetricsPluginExtension.INDEX_PREFIX, BUILD_TYPE, 'id', 1, true)
        builder.setSource(_) >> builder
        builder.setId(_) >> builder
        builder.execute() >> future
        future.actionGet() >> response
        builder
    }

    def IndicesExistsRequestBuilder mockIndiciesExistsBuilder() {
        def builder = Mock(IndicesExistsRequestBuilder)
        def future = Mock(ListenableActionFuture)
        def response = new IndicesExistsResponse(true)
        builder.execute() >> future
        future.actionGet() >> response
        builder
    }
}
