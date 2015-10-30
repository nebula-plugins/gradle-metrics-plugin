package nebula.plugin.metrics.dispatcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import nebula.plugin.metrics.MetricsPluginExtension;
import net.logstash.logback.composite.JsonProviders;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.layout.LogstashLayout;
import org.gradle.logging.internal.LogEvent;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractEsMetricsDispatchr extends AbstractMetricsDispatcher {

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


    public AbstractEsMetricsDispatchr(MetricsPluginExtension extension, boolean async) {
        super(extension, async);
    }

    @Override
    protected String getLogEventIndexName() {
        return extension.getLogstashIndexName();
    }

    @Override
    protected String renderEvent(LogEvent event) {
        checkNotNull(event);
        LogstashLayout logstashLayout = logstashLayoutSupplier.get();
        String message = String.format("[%s] %s", event.getCategory(), event.getMessage());
        @SuppressWarnings("ConstantConditions")
        LoggingEvent loggingEvent = new LoggingEvent(Logger.class.getCanonicalName(), logbackLogger, Level.valueOf(event.getLogLevel().name()),
                message, event.getThrowable(), null);
        return logstashLayout.doLayout(loggingEvent);
    }

    @Override
    protected void postShutDown() throws Exception {
        super.postShutDown();
        logstashLayoutSupplier.get().stop();
    }

    @Override
    public Optional<String> receipt() {
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

    protected abstract void createIndex(String indexName, String source);

    protected abstract boolean exists(String indexName);

}
