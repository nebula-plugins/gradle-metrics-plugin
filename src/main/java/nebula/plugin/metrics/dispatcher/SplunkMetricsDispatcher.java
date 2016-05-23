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

package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.model.*;
import nebula.plugin.metrics.MetricsPluginExtension;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

import org.apache.http.entity.ContentType;
import org.apache.http.client.fluent.Request;
import org.apache.http.StatusLine;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.http.HttpStatus.SC_OK;

public class SplunkMetricsDispatcher extends RestMetricsDispatcher {

    protected static final String HTTP_COLLECTOR = "HTTP_COLLECTOR";
    protected static final String FORWARDER = "FORWARDER";
    
    private Boolean submit = false;
    private String error = null;

    public SplunkMetricsDispatcher(MetricsPluginExtension extension) {
        super(extension);
        buildId = Optional.of(UUID.randomUUID().toString());
    }

	@Override
	protected Object transformBuild(Build build) {
        checkNotNull(build);
    	
        /*
        *  The only event we want to submit to splunk is the complete build info 
        */
        if(build.getEvents().isEmpty()){
            this.submit = false;
        } else {
            this.submit = true;
        }

        return build;
    }

    @Override
    protected String index(String indexName, String type, String source, Optional<String> id){
        checkNotNull(indexName);
        checkNotNull(type);
        checkNotNull(source);
        checkNotNull(id);

        String requestBody = getSplunkRequestBody(source, buildId.get());

        if (BUILD_TYPE.equals(type) && submit && requestBody != null) {
            postToSplunk(requestBody);
        }

    	return buildId.get();
    }

    @Override
    public Optional<String> receipt() {
        if (error == null) {
            return Optional.of(String.format("Metrics have been posted to %s (buildId: %s)", 
                extension.getSplunkUri(), buildId.get()));
        } else {
            return Optional.of(String.format("Could not post metrics : %s ",error));
        }
    }

    private void postToSplunk(String requestBody) {
        try {

            Request postReq = Request.Post(extension.getSplunkUri());
            postReq.bodyString(requestBody , ContentType.APPLICATION_JSON);
            postReq = addHeaders(postReq);
            StatusLine status = postReq.execute().returnResponse().getStatusLine();

            if (SC_OK != status.getStatusCode()) {
                error = String.format("%s (status code: %s)", 
                    status.getReasonPhrase(), status.getStatusCode());
            }
        } catch (IOException e) {
            error = e.getMessage();
        }
    }

    private String getSplunkRequestBody(String buildInfo, String buildId) {
        
        String body = null;

        switch(extension.getSplunkInputType()){
            case HTTP_COLLECTOR: 
                body = String.format("{\"event\": {\"buildId\": \"%s\", \"buildInfo\": %s}}",
                    buildId, buildInfo);
                break;
            case FORWARDER:
                body = String.format("{\"buildId\": \"%s\", \"buildInfo\": %s}",
                    buildId, buildInfo);
                break;
        }
        return body;
    }

    private Request addHeaders(Request req) {
        for (Map.Entry<String, String> entry : extension.getHeaders().entrySet()) {
            req.addHeader(entry.getKey().toString(),entry.getValue().toString());
        }
        return req;
    }
}
