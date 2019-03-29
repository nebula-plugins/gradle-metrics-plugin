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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import nebula.plugin.metrics.MetricsPluginExtension;
import net.logstash.logback.composite.JsonProviders;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.layout.LogstashLayout;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractESMetricsDispatcher extends AbstractMetricsDispatcher {

    private final ch.qos.logback.classic.Logger logbackLogger = new LoggerContext().getLogger(Logger.ROOT_LOGGER_NAME);
    private final Supplier<LogstashLayout> logstashLayoutSupplier = Suppliers.memoize(new Supplier<LogstashLayout>() {
        @Override
        public LogstashLayout get() {
            checkState(buildId.isPresent(), "buildId has not been set");
            final LogstashLayout layout = new LogstashLayout();
            /**
             * Gradle doesn't include a complete SLF4J implementation, so when the provider tries to access MDC
             * features a warning is output. So we need to expose a method to remove the provider.
             */
            JsonProviders<ILoggingEvent> providers = layout.getProviders();
            MdcJsonProvider provider = FluentIterable.from(providers.getProviders()).filter(MdcJsonProvider.class).first().get();
            layout.getProviders().removeProvider(provider);
            layout.setTimeZone("UTC");
            layout.setCustomFields(String.format("{\"@source\":\"%s\"}", buildId.get()));
            layout.start();
            return layout;
        }
    });


    public AbstractESMetricsDispatcher(MetricsPluginExtension extension, boolean async) {
        super(extension, async);
    }

    private LogstashLayout safeLogstashLayoutGet() {
        try {
            return logstashLayoutSupplier.get();
        } catch (Exception e) {
            logger.debug("Unable to log event due to errors in initialization", e);
            return null;
        }
    }

    @Override
    protected void postShutDown() throws Exception {
        super.postShutDown();
        try {
            LogstashLayout logstashLayout = safeLogstashLayoutGet();
            if (logstashLayout != null) {
                logstashLayout.stop();
            }
        } catch (Exception ignored) {}
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

    private String readFromFileOrDefault(String file, String defaultFile) throws IOException {
        InputStream input = (file == null || "".equals(file)) ? this.getClass().getResourceAsStream(defaultFile) : new FileInputStream(file);
        StringWriter sw = new StringWriter();
        IOUtils.copy(input, sw);
        return sw.toString();
    }

    @Override
    protected void initDatastore() {
        String indexName = extension.getIndexName();
        try {
            if (!exists(indexName)) {
                String mapping = readFromFileOrDefault(extension.getMetricsIndexMappingFile(), "/default-build-metrics-mappings.json");
                logger.info(String.format("Creating index %s for metrics", indexName));
                createIndex(indexName, mapping);
            } else {
                logger.info(String.format("Using index %s for metrics", indexName));
            }
        } catch (IOException e) {
            logger.debug("Error creating index(es)", e);
        }
    }

    @Override
    protected String getCollectionName() {
        return extension.getIndexName();
    }

    protected String getURI(MetricsPluginExtension extension) {
        return extension.getFullURI() != null ? extension.getFullURI() : "http://" + extension.getHostname() + ":" + extension.getHttpPort();
    }

    protected abstract void createIndex(String indexName, String source);

    protected abstract boolean exists(String indexName);

}
