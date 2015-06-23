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

import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.MetricsPluginExtension;
import nebula.plugin.metrics.model.*;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import net.logstash.logback.layout.LogstashLayout;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractESMetricsDispatcher extends AbstractQueuedExecutionThreadService<Runnable> implements MetricsDispatcher {
    protected static final String BUILD_TYPE = "build";
    protected static final String LOG_TYPE = "log";

    protected final Logger logger = MetricsLoggerFactory.getLogger(this.getClass());
    protected final MetricsPluginExtension extension;

    private final ObjectMapper mapper;
    private final boolean async;
    private final Build build;
    private final Supplier<LogstashLayout> logstashLayoutSupplier = Suppliers.memoize(new Supplier<LogstashLayout>() {
        @Override
        public LogstashLayout get() {
            checkState(buildId.isPresent(), "buildId has not been set");
            LogstashLayout layout = new LogstashLayout();
            layout.setTimeZone("UTC");
            layout.setCustomFields(String.format("{\"@source\":\"%s\"}", buildId.get()));
            layout.start();
            return layout;
        }
    });
    private Optional<String> buildId = Optional.absent();

    protected AbstractESMetricsDispatcher(MetricsPluginExtension extension) {
        this(extension, true);
    }

    protected AbstractESMetricsDispatcher(MetricsPluginExtension extension, boolean async) {
        super(true);
        this.extension = checkNotNull(extension);
        this.mapper = getObjectMapper();
        this.async = async;
        this.build = new Build();
    }

    @VisibleForTesting
    static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JodaModule());
        registerEnumModule(mapper);
        return mapper;
    }

    /**
     * Register Jackson module that maps enums as lowercase. Per http://stackoverflow.com/a/24173645.
     */
    @SuppressWarnings("rawtypes")
    private static void registerEnumModule(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<Enum> modifyEnumDeserializer(DeserializationConfig config,
                                                                 final JavaType type,
                                                                 BeanDescription beanDesc,
                                                                 final JsonDeserializer<?> deserializer) {
                return new JsonDeserializer<Enum>() {
                    @Override
                    public Enum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                        @SuppressWarnings("unchecked") Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
                        return Enum.valueOf(rawClass, jp.getValueAsString().toUpperCase());
                    }
                };
            }
        });
        module.addSerializer(Enum.class, new StdSerializer<Enum>(Enum.class) {
            @Override
            public void serialize(Enum value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                jgen.writeString(value.name().toLowerCase());
            }
        });
        mapper.registerModule(module);
    }

    @Override
    protected final boolean isAsync() {
        return async;
    }

    @Override
    protected final void execute(Runnable runnable) throws Exception {
        runnable.run();
    }

    @Override
    protected final void startUp() throws Exception {
        // This won't be accurate, but we at least want a value here if we have a failure that causes the duration not to be fired
        build.setStartTime(System.currentTimeMillis());
        startUpClient();
        indexBuild();
    }

    protected abstract void startUpClient();

    @Override
    protected final void postShutDown() throws Exception {
        shutDownClient();
        logstashLayoutSupplier.get().stop();
    }

    protected abstract void shutDownClient();

    @Override
    protected void beforeShutDown() {
        indexBuild();
    }

    @Override
    public final Optional<String> receipt() {
        if (buildId.isPresent()) {
            String file = "/" + extension.getIndexName() + "/" + BUILD_TYPE + "/" + buildId.get();
            URL url;
            try {
                url = new URL("http", extension.getHostname(), extension.getHttpPort(), file);
            } catch (MalformedURLException e) {
                throw Throwables.propagate(e);
            }
            return Optional.of("You can find the metrics for this build at " + url);
        } else {
            return buildId;
        }
    }

    private void indexBuild() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String json = mapper.writeValueAsString(build);
                    if (buildId.isPresent()) {
                        index(extension.getIndexName(), BUILD_TYPE, json, buildId);
                    } else {
                        buildId = Optional.of(index(extension.getIndexName(), BUILD_TYPE, json));
                        logger.info("Build id is {}", buildId.get());
                    }
                } catch (JsonProcessingException e) {
                    logger.error("Unable to write JSON string value: " + e.getMessage(), e);
                }
            }
        };
        queue(runnable);
    }

    @Override
    public final void started(Project project) {
        build.setProject(project);
        indexBuild();
    }

    protected abstract void createIndex(String indexName, String source);

    protected final String index(String indexName, String type, String source) {
        return index(indexName, type, source, Optional.<String>absent());
    }

    protected abstract String index(String indexName, String type, String source, Optional<String> id);

    protected abstract void bulkIndex(String indexName, String type, Collection<String> sources);

    protected abstract boolean exists(String indexName);

    @Override
    public final void duration(long startTime, long elapsedTime) {
        build.setStartTime(startTime);
        build.setElapsedTime(elapsedTime);
    }

    @Override
    public final void environment(Info info) {
        build.setInfo(info);
    }

    @Override
    public final void result(Result result) {
        build.setResult(result);
    }

    @Override
    public final void event(String description, String type, long elapsedTime) {
        build.addEvent(new Event(description, type, elapsedTime));
    }

    @Override
    public final void task(Task task) {
        build.addTask(task);
    }

    @Override
    public final void logbackEvent(final LoggingEvent event) {
        checkNotNull(event);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String json = layoutLogbackEvent(event);
                index(extension.getLogstashIndexName(), LOG_TYPE, json);
            }
        };
        queue(runnable);
    }

    @Override
    public final void logbackEvents(final Collection<LoggingEvent> events) {
        checkNotNull(events);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                List<String> jsons = Lists.newArrayListWithCapacity(events.size());
                for (LoggingEvent event : events) {
                    jsons.add(layoutLogbackEvent(event));
                }
                bulkIndex(extension.getLogstashIndexName(), LOG_TYPE, jsons);
            }
        };
        queue(runnable);
    }

    private String layoutLogbackEvent(LoggingEvent event) {
        LogstashLayout logstashLayout = logstashLayoutSupplier.get();
        return logstashLayout.doLayout(event);
    }

    @Override
    public final void test(Test test) {
        build.addTest(test);
    }
}
