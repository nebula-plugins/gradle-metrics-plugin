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
import com.google.common.base.Throwables;
import java.net.MalformedURLException;
import java.net.URL;
import nebula.plugin.metrics.MetricsPluginExtension;

public abstract class AbstractESMetricsDispatcher extends AbstractMetricsDispatcher {

    public AbstractESMetricsDispatcher(MetricsPluginExtension extension, boolean async) {
        super(extension, async);
    }

    @Override
    public Optional<String> receipt() {
        if (buildId.isPresent()) {
            String file = "/" + extension.getIndexName() + "/" + BUILD_TYPE + "/" + buildId.get();
            URL url;
            try {
                url = new URL(getURI(extension) + file);
            } catch (MalformedURLException e) {
                throw Throwables.propagate(e);
            }
            return Optional.of("You can find the metrics for this build at " + url);
        } else {
            return Optional.absent();
        }
    }

    @Override
    protected String getCollectionName() {
        return extension.getIndexName();
    }

    protected String getURI(MetricsPluginExtension extension) {
        return extension.getFullURI() != null ? extension.getFullURI() : "http://" + extension.getHostname() + ":" + extension.getHttpPort();
    }

    protected abstract boolean exists(String indexName);

}
