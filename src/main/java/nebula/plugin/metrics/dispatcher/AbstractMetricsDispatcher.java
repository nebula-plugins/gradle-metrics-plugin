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
import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.MetricsPluginExtension;
import nebula.plugin.metrics.model.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractMetricsDispatcher extends AbstractQueuedExecutionThreadService<Runnable> implements MetricsDispatcher {
    protected static final String BUILD_TYPE = "build";
    protected static final String LOG_TYPE = "log";

    protected final Logger logger = MetricsLoggerFactory.getLogger(this.getClass());
    protected final MetricsPluginExtension extension;

    protected final ObjectMapper mapper;
    private final boolean async;
    private final Build build;

    protected Optional<String> buildId = Optional.absent();

    @VisibleForTesting
    public static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JodaModule());
        registerEnumModule(mapper);
        return mapper;
    }

    protected AbstractMetricsDispatcher(MetricsPluginExtension extension, boolean async) {
        super(extension.isFailOnError(), extension.isVerboseErrorOutput());
        this.extension = checkNotNull(extension);
        this.mapper = getObjectMapper();
        this.async = async;
        this.build = new Build();
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
        initDatastore();

        // indexBuildModel must be executed synchronously and block the service from continuing to "Running" state
        // because we want to record the state before the build started running. If we don't do this, we're risking
        // a race condition where the build finishes by the time the model is actually indexed.
        indexBuildModel(true);
    }

    @Override
    protected void postShutDown() throws Exception {
        shutDownClient();
    }

    @Override
    protected void beforeShutDown() {
        // this indexBuildModel also must be executed synchronously or Gradle might kill the Service before
        // the dispatcher completes its work to upload the final build results.
        indexBuildModel(true);
    }

    @Override
    public Optional<String> receipt() {
        // by default, metrics dispatchers cannot provide a receipt. Concrete classes may change this behavior.
        return Optional.absent();
    }

    private void indexBuildModel(boolean executeSynchronously) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    sanitizeProperties(build);
                    assignBuildId(build);
                    Object transformed = transformBuild(build);
                    String json = mapper.writeValueAsString(transformed);
                    buildId = Optional.of(index(getCollectionName(), BUILD_TYPE, json, buildId));
                    logger.info("Build id is {}", buildId.get());
                } catch (JsonProcessingException e) {
                    logger.error("Unable to write JSON string value", e);
                }
            }
            @Override
            public String toString() {
                return "AbstractMetricsDispatcher.indexBuildModel()";
            }
        };

        if (executeSynchronously) {
            executeSynchronously(runnable);
        } else {
            queue(runnable);
        }
    }

    private void assignBuildId(Build build) {
        if(buildId.isPresent()) {
            build.setBuildId(buildId.get());
        }
    }
    /*
     * Override this method to transform the Build object into a different Build representation. For
     * example, when the Build format needs to be flattened out.
     */
    protected Object transformBuild(Build build) {
        checkNotNull(build);
        return build;
    }

    private void sanitizeProperties(Build build) {
        Info info = build.getInfo();
        if (info != null) {
            build.setInfo(Info.sanitize(info, extension.getSanitizedProperties(), extension.getSanitizedPropertiesRegex()));
        }
    }

    @Override
    public final void started(Project project) {
        build.setProject(project);
        indexBuildModel(false);
    }

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
    public final void test(Test test) {
        build.addTest(test);
    }

    @Override
    public void report(String reportName, Object report) {
        checkNotNull(reportName);
        checkNotNull(report);
        build.addBuildReport(reportName, report);
    }

    protected void startUpClient() {
        // empty implementation. Concrete classes may override to add functionality.
    }

    protected void shutDownClient() {
        // empty implementation. Concrete classes may override to add functionality.
    }

    protected void initDatastore() {
        // empty implementation. Concrete classes may override to add functionality.
    }

    // the collection name is where the build data gets uploaded to.
    // In Elastic this is the index name. In REST payloads it's the eventName.
    protected abstract String getCollectionName();

    protected abstract String index(String indexName, String type, String source, Optional<String> id);

    protected abstract void bulkIndex(String indexName, String type, Collection<String> sources);
}
