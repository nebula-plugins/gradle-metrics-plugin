/*
 * Copyright 2015 the original author or authors.
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

import nebula.plugin.metrics.dispatcher.MetricsDispatcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.TurboFilterList;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector that intercepts Logback events by binding an appender to a provided {@link Logger}.
 *
 * @author Danny THomas
 */
public class LogbackCollector {
    /**
     * Thread local to prevent stack overflows when logging statements within the event dispatcher chain log while
     * processing another event.
     */
    private static final ThreadLocal<Boolean> IN_FILTER = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    /**
     * Configure a logback filter to capture all root logging events.
     * </p>
     * Avoids having to depend on a particular Gradle logging level being set. Gradle's logging is such that
     * encoders/layouts/etc aren't an option and LogbackLoggingConfigurer.doConfigure() adds a TurboFilter which
     * prevents us getting at those events, so we re-wire the filters so ours comes first.
     */
    public static void configureLogbackCollection(final MetricsDispatcher dispatcher) {
        checkNotNull(dispatcher);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilter metricsFilter = new TurboFilter() {
            @Override
            public FilterReply decide(Marker marker, Logger logger, Level level, String s, Object[] objects, Throwable throwable) {
                if (IN_FILTER.get()) {
                    return FilterReply.NEUTRAL;
                }
                try {
                    IN_FILTER.set(true);
                    if (level.isGreaterOrEqual(Level.INFO)) { // TODO make configurable
                        LoggingEvent event = new LoggingEvent(Logger.class.getCanonicalName(), logger, level, s, throwable, objects);
                        dispatcher.logbackEvent(event);
                    }
                    return FilterReply.NEUTRAL;
                } finally {
                    IN_FILTER.set(false);
                }
            }
        };
        TurboFilterList filterList = context.getTurboFilterList();
        if (!filterList.isEmpty()) {
            TurboFilter gradleFilter = filterList.get(0);
            context.resetTurboFilterList();
            context.addTurboFilter(metricsFilter);
            context.addTurboFilter(gradleFilter);
        } else {
            context.resetTurboFilterList();
            context.addTurboFilter(metricsFilter);
        }
    }
}
