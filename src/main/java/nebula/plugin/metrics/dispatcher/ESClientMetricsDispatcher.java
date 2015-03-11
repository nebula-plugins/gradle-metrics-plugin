package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.MetricsExtension;
import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.model.*;

import autovalue.shaded.com.google.common.common.collect.Lists;
import autovalue.shaded.com.google.common.common.collect.Maps;
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
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import net.logstash.logback.layout.LogstashLayout;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Preconditions.*;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Elasticsearch client {@link nebula.plugin.metrics.dispatcher.MetricsDispatcher}.
 *
 * TODO I'm not quite sure about the Runnable pattern. It's a nice way of ensuring failure can't bubble up to callers, but maybe I can do better...
 *
 * @author Danny Thomas
 */
public class ESClientMetricsDispatcher extends AbstractQueuedExecutionThreadService<Runnable> implements MetricsDispatcher {
    protected static final String BUILD_METRICS_INDEX = "build-metrics";
    protected static final String BUILD_TYPE = "build";
    protected static final String LOG_TYPE = "log";
    protected static final Map<String, List<String>> NESTED_MAPPINGS;

    static {
        NESTED_MAPPINGS = Maps.newLinkedHashMap();
        for (String mapping : Arrays.asList("events", "tasks", "logs", "tests", "artifacts")) {
            NESTED_MAPPINGS.put(mapping, ImmutableList.<String>of());
        }
        NESTED_MAPPINGS.put("environment", ImmutableList.of("environmentVariables", "systemProperties"));
    }

    protected final Logger logger = MetricsLoggerFactory.getLogger(this.getClass());
    protected final MetricsExtension extension;
    private final Client client;
    private final ObjectMapper mapper;
    private final boolean async;
    private final LogstashLayout logstashLayout;
    private final BlockingQueue<LoggingEvent> logbackEvents;
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
        logstashLayout = new LogstashLayout();
        logbackEvents = new LinkedBlockingQueue<>();
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
    protected boolean isAsync() {
        return async;
    }

    @Override
    protected void startUp() throws Exception {
        createIndexesIfNeeded();
    }

    @Override
    protected void execute(Runnable runnable) throws Exception {
        runnable.run();
    }

    private void createIndexesIfNeeded() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final IndicesExistsRequestBuilder indicesExists = client.admin().indices().prepareExists(BUILD_METRICS_INDEX);
                IndicesExistsResponse indicesExistsResponse = indicesExists.execute().actionGet();
                if (!indicesExistsResponse.isExists()) {
                    createBuildIndex(NESTED_MAPPINGS);
                }
            }
        };
        queue(runnable);
    }

    @Override
    protected void postShutDown() throws Exception {
        flushLogbackEvents(); // One last flush to to make sure we got everything
        if (!logbackEvents.isEmpty()) {
            logger.error("Not all logback events were successfully flushed: {}", logbackEvents);
        }
        client.close();
        logstashLayout.stop();
    }

    private void flushLogbackEvents() {
        if (buildId == null) {
            logger.debug("Skipping logback event flush, as buildId has not been set");
            return;
        }
        if (!logstashLayout.isStarted()) {
            logstashLayout.setTimeZone("UTC");
            logstashLayout.setCustomFields(String.format("{\"@source\":\"%s\"}", buildId));
            logstashLayout.start();
        }
        final List<LoggingEvent> events = Lists.newArrayListWithExpectedSize(logbackEvents.size());
        logbackEvents.drainTo(events);
        if (!events.isEmpty()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    BulkRequestBuilder bulk = client.prepareBulk();
                    for (LoggingEvent event : events) {
                        IndexRequestBuilder index = client.prepareIndex(BUILD_METRICS_INDEX, LOG_TYPE);
                        index.setSource(logstashLayout.doLayout(event));
                        bulk.add(index);
                    }
                }
            };
            queue(runnable);
        }
    }

    @VisibleForTesting
    String getBuildId() {
        return buildId;
    }

    private void checkBuildStarted() {
        checkNotNull(build, "Build is null. Has buildStarted() been called?");
    }

    @Override
    public void started(Project project) {
        checkState(build == null, "Build is not null. Duplicate call to started()?");
        build = new Build(project);
        // This won't be accurate, but we at least want a value here if we have a failure that causes the duration not to be fired
        build.setStartTime(System.currentTimeMillis());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String json = mapper.writeValueAsString(build);
                    IndexRequestBuilder index = client.prepareIndex(BUILD_METRICS_INDEX, BUILD_TYPE).setSource(json);
                    IndexResponse indexResponse = index.execute().actionGet();
                    assert indexResponse.isCreated() : "Response should always be created";
                    buildId = indexResponse.getId();
                    logger.warn("Build id is {}", buildId);
                } catch (JsonProcessingException e) {
                    logger.error("Unable to write JSON string value: " + e.getMessage(), e);
                }
            }
        };
        queue(runnable);
    }

    private void createBuildIndex(Map<String, List<String>> nestedTypes) {
        try {
            XContentBuilder jsonBuilder = jsonBuilder();
            jsonBuilder.startObject().startObject("mappings").startObject(BUILD_TYPE);
            jsonBuilder.startObject("_timestamp").field("enabled", true).field("path", "build.startTime").endObject();
            jsonBuilder.startObject("properties");
            for (Map.Entry<String, List<String>> entry : nestedTypes.entrySet()) {
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
            CreateIndexRequestBuilder indexCreate = client.admin().indices().prepareCreate(BUILD_METRICS_INDEX).setSource(jsonBuilder);
            indexCreate.execute().actionGet();
            // TODO do I need to check the response here?
        } catch (IOException e) {
            throw com.google.common.base.Throwables.propagate(e);
        }
    }

    @Override
    public void duration(long startTime, long elapsedTime) {
        build.setStartTime(startTime);
        build.setElapsedTime(elapsedTime);
    }

    @Override
    public void environment(Environment environment) {
        checkBuildStarted();
        build.setEnvironment(environment);
    }

    @Override
    public void result(Result result) {
        checkBuildStarted();
        build.setResult(result);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String json = mapper.writeValueAsString(build);
                    IndexRequestBuilder index = client.prepareIndex(BUILD_METRICS_INDEX, BUILD_TYPE).setSource(json).setId(buildId);
                    index.execute().actionGet();
                } catch (JsonProcessingException e) {
                    Throwables.propagate(e);
                }
            }
        };
        queue(runnable);
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
    public void logbackEvent(LoggingEvent event) {
        // We need the buildId for the logging event source, so we need to defer logging events until the buildId is set
        logbackEvents.add(event); // TODO probably need a different queue implementation
        flushLogbackEvents();
    }

    @Override
    public void test(Test test) {
        checkBuildStarted();
        build.addTest(test);
    }
}
