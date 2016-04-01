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

import com.google.common.base.Optional;
import nebula.plugin.metrics.MetricsPluginExtension;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class NoopMetricsDispatcher extends AbstractESMetricsDispatcher {
    private AtomicInteger counter = new AtomicInteger(0);

    public NoopMetricsDispatcher(MetricsPluginExtension extension) {
        super(extension, false);
    }

    @Override
    protected void createIndex(String indexName, String source) {
    }

    @Override
    protected boolean exists(String indexName) {
        return true;
    }

    @Override
    protected String index(String indexName, String type, String source, Optional<String> id) {
        return ((Integer) counter.incrementAndGet()).toString();
    }

    @Override
    protected void bulkIndex(String indexName, String type, Collection<String> sources) {
        counter.addAndGet(sources.size());
    }
}
