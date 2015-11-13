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
