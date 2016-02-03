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

package nebula.plugin.metrics.dispatcher;

import com.google.common.base.Optional;
import nebula.plugin.metrics.MetricsPluginExtension;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Elasticsearch client {@link nebula.plugin.metrics.dispatcher.MetricsDispatcher}.
 *
 * @author Danny Thomas
 */
public final class ClientESMetricsDispatcher extends AbstractESMetricsDispatcher {
    private Client client;

    public ClientESMetricsDispatcher(MetricsPluginExtension extension) {
        this(extension, null, true);
    }

    ClientESMetricsDispatcher(MetricsPluginExtension extension, @Nullable Client client, boolean async) {
        super(extension, async);
        this.client = client;
    }

    @Override
    protected void startUpClient() {
        if (client == null) {
            client = createTransportClient(extension);
        }
    }

    @Override
    protected void shutDownClient() {
        client.close();
    }

    private Client createTransportClient(MetricsPluginExtension extension) {
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder();
        builder.classLoader(Settings.class.getClassLoader());
        builder.put("cluster.name", extension.getClusterName());
        InetSocketTransportAddress address = new InetSocketTransportAddress(extension.getHostname(), extension.getTransportPort());
        return new TransportClient(builder.build()).addTransportAddress(address);
    }

    @Override
    protected void createIndex(String index, String source) {
        CreateIndexRequestBuilder indexCreate = client.admin().indices().prepareCreate(index).setSource(source);
        indexCreate.execute().actionGet();
    }

    @Override
    protected String index(String indexName, String type, String source, Optional<String> id) {
        IndexRequestBuilder index = client.prepareIndex(extension.getIndexName(), type).setSource(source);
        index.setId(id.or(UUID.randomUUID().toString()));
        IndexResponse indexResponse = index.execute().actionGet();
        return indexResponse.getId();
    }

    @Override
    protected void bulkIndex(String indexName, String type, Collection<String> sources) {
        BulkRequestBuilder bulk = client.prepareBulk();
        for (String source : sources) {
            IndexRequestBuilder index = client.prepareIndex(extension.getIndexName(), type);
            index.setSource(source);
            bulk.add(index);
        }
        bulk.execute().actionGet();
    }

    @Override
    protected boolean exists(String indexName) {
        final IndicesExistsRequestBuilder indicesExists = client.admin().indices().prepareExists(extension.getIndexName());
        IndicesExistsResponse indicesExistsResponse = indicesExists.execute().actionGet();
        return indicesExistsResponse.isExists();
    }
}
