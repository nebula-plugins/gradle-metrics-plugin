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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import nebula.plugin.metrics.MetricsPluginExtension;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.http.client.fluent.Request.Post;

public class SuroMetricsDispatcher extends AbstractMetricsDispatcher {

    protected SuroMetricsDispatcher(MetricsPluginExtension extension, boolean async) {
        super(extension, async);
    }

    @Override
    protected void startUpClient() {
        // safely ignore client startup. This is not needed for a REST client.
    }

    @Override
    protected void shutDownClient() {
        // safely ignore client shutdown. This is not needed for a REST client.
    }

    @Override
    protected String getLogEventIndexName() {
        return extension.getSuroBuildLogEventName();
    }

    @Override
    protected String index(String indexName, String type, String source, Optional<String> id) {
        checkNotNull(indexName);
        checkNotNull(type);
        checkNotNull(source);

        if (!id.isPresent()) {
            id = Optional.of(UUID.randomUUID().toString());
        }
        String payload = createPayloadJson(indexName, source, id.get());
        postPayload(payload);
        return id.get();
    }

    @Override
    protected void bulkIndex(String indexName, String type, Collection<String> sources) {
        checkNotNull(indexName);
        checkNotNull(type);
        checkState(sources.size() > 0);

        List<String> payloads = Lists.newArrayList();
        for (String source : sources) {
            Optional<String> id = Optional.of(UUID.randomUUID().toString());
            payloads.add(createPayloadJson(indexName, source, id.get()));
        }

        postPayload(joinMultiplePayloads(payloads));
    }

    private void postPayload(String payload) {
        checkNotNull(payload);

        String url = buildUrl();

        try {
            Post(url).bodyString(payload, ContentType.APPLICATION_JSON).execute();
        } catch (IOException e) {
            throw new RuntimeException("Unable to POST to Suro at " + url, e);
        }
    }

    private String buildUrl() {
        String protocol = extension.isSuroHttps() ? "https" : "http";
        return String.format("%s://%s:%d/REST/v1/log", protocol, extension.getHostname(), extension.getSuroPort());
    }

    private String createPayloadJson(String indexName, String payload, String buildId) {
        // I really dislike Java
        return String.format("{ " +
                "\"eventName\" : \"%s\", " +
                "\"payload\" : { \"buildId\": \"%s\", \"build\": \"%s\" }" +
                "}", indexName, buildId, payload);
    }

    private String joinMultiplePayloads(Collection<String> payloads) {
        Joiner joiner = Joiner.on(", ").skipNulls();
        return String.format("[ %s ]", joiner.join(payloads));
    }

}
