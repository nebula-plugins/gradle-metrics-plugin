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

package nebula.plugin.metrics.dispatcher;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.IndicesExists;
import nebula.plugin.metrics.MetricsPluginExtension;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * Elasticsearch HTTP {@link nebula.plugin.metrics.dispatcher.MetricsDispatcher}.
 *
 * @author Danny Thomas
 */
public final class HttpESMetricsDispatcher extends AbstractESMetricsDispatcher {
    private JestClient client;

    public HttpESMetricsDispatcher(MetricsPluginExtension extension) {
        super(extension, true);
    }

    @Override
    protected void startUpClient() {
        JestClientFactory factory = new JestClientFactory();
        HttpClientConfig.Builder config = new HttpClientConfig
                .Builder(getURI(this.extension))
                .multiThreaded(false);
        if (!Strings.isNullOrEmpty(extension.getEsBasicAuthUsername())) {
            config.defaultCredentials(extension.getEsBasicAuthUsername(), extension.getEsBasicAuthPassword());
        }
        factory.setHttpClientConfig(config.build());
        client = factory.getObject();
    }

    @Override
    protected void shutDownClient() {
        client.shutdownClient();
    }

    @Override
    protected void createIndex(String indexName, String source) {
        CreateIndex createIndex = new CreateIndex.Builder(indexName).settings(source).build();
        execute(createIndex, false);
    }

    @Override
    protected String index(String indexName, String type, String source, Optional<String> id) {
        if (!id.isPresent()) {
            id = Optional.of(UUID.randomUUID().toString());
        }
        Index index = buildIndex(indexName, type, source, id.get());
        JestResult result = execute(index, false);
        return result.getJsonObject().get("_id").getAsString();
    }

    private Index buildIndex(String indexName, String type, String source, String id) {
        Index.Builder builder = new Index.Builder(source).index(indexName).type(type).id(id);
        return builder.build();
    }

    @Override
    protected void bulkIndex(String indexName, String type, Collection<String> sources) {
        Bulk.Builder builder = new Bulk.Builder();
        for (String source : sources) {
            String id = UUID.randomUUID().toString();
            builder.addAction(buildIndex(indexName, type, source, id));
        }
        Bulk bulk = builder.build();
        execute(bulk, false);
    }

    @Override
    protected boolean exists(String indexName) {
        IndicesExists indicesExists = new IndicesExists.Builder(indexName).build();
        JestResult result = execute(indicesExists, true);
        return result.getJsonObject().get("found").getAsBoolean();
    }

    private <T extends JestResult> T execute(Action<T> clientRequest, boolean allowNotFound) {
        try {
            T result = client.execute(clientRequest);
            if (!result.isSucceeded() && (!allowNotFound || result.getResponseCode() != 404)) {
                throw new RuntimeException("Jest request failed with " + result.getErrorMessage());
            }
            return result;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
