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

package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.MetricsPluginExtension;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Service;
import org.gradle.api.logging.LogLevel;
import org.gradle.logging.internal.LogEvent;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector that intercepts logging events.
 *
 * @author Danny Thomas
 */
public class LoggingCollector {
    private static final ThreadLocal<Boolean> IN_LISTENER = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };
    private static final Logger LOGGER = MetricsLoggerFactory.getLogger(LoggingCollector.class);

    /**
     * Configure a logback filter to capture all root logging events.
     *
     * Avoids having to depend on a particular Gradle logging level being set. Gradle's logging is such that
     * encoders/layouts/etc aren't an option and LogbackLoggingConfigurer.doConfigure() adds a TurboFilter which
     * prevents us getting at those events, so we re-wire the filters so ours comes first.
     *
     * @param dispatcherSupplier the dispatcher supplier
     * @param extension          the extension
     */
    public static void configureCollection(final Supplier<MetricsDispatcher> dispatcherSupplier, final MetricsPluginExtension extension) {
        checkNotNull(dispatcherSupplier);
        checkNotNull(extension);
        final BlockingQueue<LogEvent> logEvents = new LinkedBlockingQueue<>();
        OutputEventListenerBackedLoggerContext context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
        // setting LogLevel to DEBUG forces stdout messages to be classified as DEBUG events, which are then discarded and never shown.
        //context.setLevel(LogLevel.DEBUG);
        OutputEventListener originalListener = context.getOutputEventListener();
        if (dispatcherSupplier.toString().startsWith("Dummy proxy")) {
            // Horrible coupled logic, but we need to keep the Guava nulls testers out of here
            return;
        }
        if (originalListener.getClass().getName().startsWith("nebula.plugin.metrics.collector.LoggingCollector")) {
            LOGGER.error("Output event listener is already wrapped. A previous build against this daemon did not clean reset the logging collection. Please report this bug");
            return;
        }
        OutputEventListener listener = new WrappedOutputEventListener(originalListener) {
            @Override
            public void onOutput(OutputEvent outputEvent) {
                if (IN_LISTENER.get()) {
                    return;
                }
                IN_LISTENER.set(true);
                try {
                    if (outputEvent instanceof LogEvent) {
                        LogEvent logEvent = (LogEvent) outputEvent;
                        if (levelGreaterOrEqual(outputEvent, extension.getLogLevel()) || logEvent.getMessage().startsWith(MetricsLoggerFactory.LOGGING_PREFIX)) {
                            MetricsDispatcher dispatcher = dispatcherSupplier.get();
                            if (dispatcher.state() == Service.State.NEW || dispatcher.state() == Service.State.STARTING) {
                                logEvents.add(logEvent);
                            } else {
                                if (!logEvents.isEmpty()) {
                                    List<LogEvent> drainedEvents = Lists.newArrayListWithCapacity(logEvents.size());
                                    logEvents.drainTo(drainedEvents);
                                    if (!drainedEvents.isEmpty()) {
                                        dispatcher.logEvents(drainedEvents);
                                    }
                                }
                                dispatcher.logEvent(logEvent);
                            }
                        }
                    }
                    super.onOutput(outputEvent);
                } finally {
                    IN_LISTENER.set(false);
                }
            }
        };
        context.setOutputEventListener(listener);
    }

    private static boolean levelGreaterOrEqual(OutputEvent outputEvent, LogLevel logLevel) {
        return outputEvent.getLogLevel().compareTo(logLevel) >= 0;
    }

    public static void reset() {
        OutputEventListenerBackedLoggerContext context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
        OutputEventListener listener = context.getOutputEventListener();
        if (!(listener instanceof WrappedOutputEventListener)) {
            throw new IllegalStateException("Expected a wrapped logging output, but instead found " + listener);
        }
        WrappedOutputEventListener wrappedListener = (WrappedOutputEventListener) listener;
        context.setOutputEventListener(wrappedListener.unwrap());
    }

    private static class WrappedOutputEventListener implements OutputEventListener {
        private final OutputEventListener listener;

        public WrappedOutputEventListener(OutputEventListener listener) {
            this.listener = checkNotNull(listener);
        }

        @Override
        public void onOutput(OutputEvent outputEvent) {
            listener.onOutput(outputEvent);
        }

        public OutputEventListener unwrap() {
            return listener;
        }
    }
}
