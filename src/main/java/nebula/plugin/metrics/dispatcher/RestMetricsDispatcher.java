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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import nebula.plugin.metrics.MetricsPluginExtension;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.http.client.fluent.Request.Post;

public class RestMetricsDispatcher extends AbstractMetricsDispatcher {

    public RestMetricsDispatcher(MetricsPluginExtension extension) {
        super(extension, true);
        buildId = Optional.of(UUID.randomUUID().toString());
    }

    @Override
    protected String getLogCollectionName() {
        return extension.getRestLogEventName();
    }

    @Override
    protected String getBuildCollectionName() {
        return extension.getRestBuildEventName();
    }

    @Override
    protected String index(String indexName, String type, String source, Optional<String> id) {
        checkNotNull(indexName);
        checkNotNull(type);
        checkNotNull(source);
        checkNotNull(id);

        // id is ignored because the REST dispatcher generates one on startup.
        String payload = createPayloadJson(indexName, type, source, buildId.get());
        postPayload(payload);
        return buildId.get();
    }

    @Override
    protected void bulkIndex(String indexName, String type, Collection<String> sources) {
        checkNotNull(indexName);
        checkNotNull(type);
        checkState(sources.size() > 0);

        List<String> payloads = Lists.newArrayList();
        for (String source : sources) {
            payloads.add(createPayloadJson(indexName, type, source, buildId.get()));
        }

        postPayload(joinMultiplePayloads(payloads));
    }

    private void postPayload(String payload) {
        checkNotNull(payload);

        try {
            Post(extension.getRestUri()).bodyString(payload, ContentType.APPLICATION_JSON).execute();
        } catch (IOException e) {
            throw new RuntimeException("Unable to POST to " + extension.getRestUri(), e);
        }
    }

    private String createPayloadJson(String indexName, String type, String payload, String buildId) {
        Map<String, String> payloadMap = Maps.newHashMap();
        payloadMap.put("buildId", buildId);
        payloadMap.put(type, payload);
        try {
            RestPayload sp = new RestPayload(indexName, payloadMap);
            return mapper.writeValueAsString(sp);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse to payload", e);
        }
    }

    private String joinMultiplePayloads(Collection<String> payloads) {
        Joiner joiner = Joiner.on(", ").skipNulls();
        return String.format("[ %s ]", joiner.join(payloads));
    }

    @VisibleForTesting
    public static class RestPayload {
        private String eventName;
        private Map<String, String> payload;

        public RestPayload() {
        }

        public RestPayload(String eventName, Map<String, String> payload) {
            this.eventName = eventName;
            this.payload = payload;
        }

        public String getEventName() {
            return eventName;
        }

        public Map<String, String> getPayload() {
            return payload;
        }

        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        public void setPayload(Map<String, String> payload) {
            this.payload = payload;
        }
    }

}
