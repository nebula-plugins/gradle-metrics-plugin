package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.MetricsExtension;
import nebula.plugin.metrics.model.*;

import autovalue.shaded.com.google.common.common.collect.Maps;
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
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.*;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Elasticsearch client {@link nebula.plugin.metrics.dispatcher.MetricsDispatcher}.
 *
 * @author Danny Thomas
 */
public class ESClientMetricsDispatcher extends AbstractQueuedExecutionThreadService<ESClientMetricsDispatcher.ActionRequestBuilderRunnable> implements MetricsDispatcher {
    protected static final String BUILD_METRICS_INDEX = "build-metrics";
    protected static final String BUILD_TYPE = "build";
    protected static final Map<String, List<String>> NESTED_MAPPINGS;

    static {
        NESTED_MAPPINGS = Maps.newLinkedHashMap();
        for (String mapping : Arrays.asList("events", "tasks", "logs", "tests", "artifacts")) {
            NESTED_MAPPINGS.put(mapping, ImmutableList.<String>of());
        }
        NESTED_MAPPINGS.put("environment", ImmutableList.of("environmentVariables", "systemProperties"));
    }

    protected final Logger logger = getLogger(this.getClass());
    protected final MetricsExtension extension;
    private final Client client;
    private final ObjectMapper mapper;
    private final boolean async;
    private Build build;
    private String buildId;

    public ESClientMetricsDispatcher(MetricsExtension extension) {
        this(extension, getTransportClient(), true);
    }

    @VisibleForTesting
    ESClientMetricsDispatcher(MetricsExtension extension, Client client, boolean async) {
        super(true);
        this.extension = checkNotNull(extension);
        this.client = checkNotNull(client);
        this.mapper = getObjectMapper();
        this.async = async;
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

    @Override
    protected void startUp() throws Exception {
        createIndexesIfNeeded();
    }

    @Override
    protected boolean isAsync() {
        return async;
    }

    /**
     * Register Jackson module that maps enums as lowercase. From http://stackoverflow.com/a/24173645
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

    /**
     * Temporary method until I have an extension for configuring these properties.
     */
    private static Client getTransportClient() {
        Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elasticsearch_dannyt").build();
        return new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
    }

    @Override
    protected void execute(ESClientMetricsDispatcher.ActionRequestBuilderRunnable runnable) throws Exception {
        runnable.run();
    }

    private void queue(ActionRequestBuilder<?, ?, ?, ?> builder, ActionListener<? extends ActionResponse> listener) {
        queue(new ActionRequestBuilderRunnable(builder, listener));
    }

    private void queue(ActionRequestBuilder<?, ?, ?, ?> builder) {
        queue(new ActionRequestBuilderRunnable(builder));
    }

    @Override
    protected void postShutDown() throws Exception {
        client.close();
    }

    @Override
    public void started(Project project) {
        checkState(build == null, "Build is not null. Duplicate call to started()?");
        build = new Build(project);
        // This won't be accurate, but we at least want a value here if we have a failure that causes the duration not to be fired
        build.setStartTime(System.currentTimeMillis());
        try {
            String json = mapper.writeValueAsString(build);
            IndexRequestBuilder builder = client.prepareIndex(BUILD_METRICS_INDEX, BUILD_TYPE).setSource(json);
            ActionListener<IndexResponse> listener = new ThrowingActionListener<IndexResponse>() {
                @Override
                public void onResponse(IndexResponse indexResponse) {
                    assert indexResponse.isCreated() : "Response should always be created";
                    buildId = indexResponse.getId();
                    logger.warn("[metrics] Build id is {}", buildId);
                }
            };
            queue(new ActionRequestBuilderRunnable(builder, listener));
        } catch (JsonProcessingException e) {
            logger.error("Unable to write JSON string value: " + e.getMessage(), e);
        }
    }

    @Override
    public void duration(long startTime, long elapsedTime) {
        build.setStartTime(startTime);
        build.setElapsedTime(elapsedTime);
    }

    private void createIndexesIfNeeded() {
        final IndicesExistsRequestBuilder builder = client.admin().indices().prepareExists(BUILD_METRICS_INDEX);
        ActionListener<IndicesExistsResponse> listener = new ThrowingActionListener<IndicesExistsResponse>() {
            @Override
            public void onResponse(IndicesExistsResponse indicesExistsResponse) {
                if (!indicesExistsResponse.isExists()) {
                    createBuildIndex(NESTED_MAPPINGS);
                }
            }
        };
        queue(builder, listener);
    }

    private void createBuildIndex(Map<String, List<String>> nestedTypes) {
        try {
            XContentBuilder jsonBuilder = jsonBuilder();
            jsonBuilder.startObject().startObject("mappings").startObject(BUILD_TYPE);
            jsonBuilder.startObject("_timestamp").field("enabled", true).field("path", "build.startTime").endObject();
            jsonBuilder.startObject("properties");
            for (Map.Entry<String,List<String>> entry : nestedTypes.entrySet()) {
                String type = entry.getKey();
                Collection<String> children = entry.getValue();
                if (children.isEmpty()) {
                    jsonBuilder.startObject(type).field("type", "nested").endObject();
                } else {
                    jsonBuilder.startObject(type).startObject("properties");
                    for (String child : children) {
                        jsonBuilder.startObject(child).field("type", "nested").endObject();
                    }
                    jsonBuilder.endObject().endObject();
                }
            }
            jsonBuilder.endObject().endObject().endObject().endObject();
            CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(BUILD_METRICS_INDEX).setSource(jsonBuilder);
            queue(new ActionRequestBuilderRunnable(builder));
        } catch (IOException e) {
            throw com.google.common.base.Throwables.propagate(e);
        }
    }

    @VisibleForTesting
    String getBuildId() {
        return buildId;
    }

    @Override
    public void environment(Environment environment) {
        checkBuildStarted();
        build.setEnvironment(environment);
    }

    private void checkBuildStarted() {
        checkNotNull(build, "Build is null. Has buildStarted() been called?");
    }

    @Override
    public void result(Result result) {
        checkBuildStarted();
        build.setResult(result);
        try {
            String json = mapper.writeValueAsString(build);
            IndexRequestBuilder builder = client.prepareIndex(BUILD_METRICS_INDEX, BUILD_TYPE).setSource(json).setId(buildId);
            queue(builder);
        } catch (JsonProcessingException e) {
            logger.error("Unable to write object JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void event(String description, String type, long elapsedTime) {
        checkBuildStarted();
        checkArgument(!description.isEmpty(), "Description may not be empty");
        checkArgument(!type.isEmpty(), "Type may not be empty");
        build.addEvent(Event.create(description, type, elapsedTime));
    }

    @Override
    public void task(Task task) {
        checkBuildStarted();
        build.addTask(task);
    }

    @Override
    public void log(LogEvent event) {
        checkBuildStarted();
        build.addLogEvent(event);
    }

    @Override
    public void test(Test test) {
        checkBuildStarted();
        build.addTest(test);
    }

    /**
     * A {@link Runnable} that executes an {@link ActionRequestBuilder}, so requests can be queued with a listener, and
     * exception handling logic can be made common.
     */
    public static class ActionRequestBuilderRunnable<R extends ActionResponse> implements Runnable {
        @SuppressWarnings("rawtypes") // FIXME
        private final ActionRequestBuilder<?, R, ?, ?> builder;
        private final ActionListener<R> listener;

        private ActionRequestBuilderRunnable(ActionRequestBuilder<?, R, ?, ?> builder) {
            this.builder = builder;
            this.listener = null;
        }

        private ActionRequestBuilderRunnable(ActionRequestBuilder<?, R, ?, ?> builder, ActionListener<R> listener) {
            this.builder = builder;
            this.listener = listener;
        }

        @Override
        public void run() {
            // We want the queue to be processed in order and Elasticsearch executes listener based requests asynchronously it seems, even when you tell it not to...
            // TODO the above makes me wonder about this design. Might need a rewrite...
            if (listener != null) {
                try {
                    R response = builder.execute().actionGet();
                    listener.onResponse(response);
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            } else {
                builder.execute().actionGet();
            }
        }

        @Override
        public String toString() {
            ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
            helper.add("builder", builder);
            helper.add("listener", listener);
            return helper.toString();
        }
    }

    private abstract static class ThrowingActionListener<E> implements ActionListener<E> {
        @Override
        public void onFailure(Throwable e) {
            throw Throwables.propagate(e);
        }
    }
}
