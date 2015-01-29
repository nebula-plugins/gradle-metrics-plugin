/*
 * Copyright 2013-2015 the original author or authors.
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
import nebula.plugin.metrics.MetricsExtension
import nebula.plugin.metrics.model.*
import org.elasticsearch.client.Client
import org.gradle.StartParameter

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
        dispatcher = new ESClientMetricsDispatcher(new MetricsExtension(), client, false)
        dispatcher.startAsync().awaitRunning()
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
        dispatcher.started(Project.create('', ''))
        dispatcher.event('description', 'type', 0)
        Gradle tool = new Gradle(new StartParameter());
        SCM scm = SCM.detect();
        CI ci = CI.detect();
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

    def checkTypeIsNested(TreeNode treeNode) {
        TextNode type = treeNode.get('type')
        assert type != null: "$treeNode  did not have a type"
        assert type.asText() == "nested": "Type was not 'nested', found '$type.asText'"
    }
}
