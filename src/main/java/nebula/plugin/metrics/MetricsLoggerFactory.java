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

package nebula.plugin.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A wrapper for {@link org.slf4j.LoggerFactory} which allows the provided logger to be wrapped to customise logging behaviour.
 * <p>
 * Gradle only uses logback appenders to gather messages, and adding custom layouts and encoders for a plugin could be troublesome.
 *
 * @author Danny Thomas
 */
public class MetricsLoggerFactory {
    public static Logger getLogger(String name) {
        Logger logger = LoggerFactory.getLogger(checkNotNull(name));
        return new MetricsLogger(logger);
    }

    public static final String LOGGING_PREFIX = "[metrics] ";

    public static Logger getLogger(Class clazz) {
        return getLogger(clazz.getName());
    }

    private static final class MetricsLogger implements Logger {
        private final Logger logger;

        private MetricsLogger(Logger logger) {
            this.logger = logger;
        }

        @Override
        public String getName() {
            return logger.getName();
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return logger.isInfoEnabled(marker);
        }

        private String addPrefix(String s) {
            return LOGGING_PREFIX + s;
        }

        @Override
        public void info(String s, Object... objects) {
            logger.info(addPrefix(s), objects);
        }

        @Override
        public void info(Marker marker, String s, Throwable throwable) {
            logger.info(marker, addPrefix(s), throwable);
        }

        @Override
        public void error(Marker marker, String s, Object... objects) {
            logger.error(marker, addPrefix(s), objects);
        }

        @Override
        public void error(String s, Throwable throwable) {
            logger.error(addPrefix(s), throwable);
        }

        @Override
        public void error(Marker marker, String s, Object o, Object o1) {
            logger.error(marker, addPrefix(s), o, o1);
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return logger.isDebugEnabled(marker);
        }

        @Override
        public void warn(String s, Object o, Object o1) {
            logger.warn(addPrefix(s), o, o1);
        }

        @Override
        public boolean isTraceEnabled() {
            return logger.isTraceEnabled();
        }

        @Override
        public void debug(Marker marker, String s, Object... objects) {
            logger.debug(marker, addPrefix(s), objects);
        }

        @Override
        public void debug(String s, Object... objects) {
            logger.debug(addPrefix(s), objects);
        }

        @Override
        public void error(Marker marker, String s) {
            logger.error(marker, addPrefix(s));
        }

        @Override
        public void info(String s, Object o, Object o1) {
            logger.info(addPrefix(s), o, o1);
        }

        @Override
        public void trace(Marker marker, String s, Object o, Object o1) {
            logger.trace(marker, addPrefix(s), o, o1);
        }

        @Override
        public void warn(String s, Throwable throwable) {
            logger.warn(addPrefix(s), throwable);
        }

        @Override
        public void warn(Marker marker, String s, Throwable throwable) {
            logger.warn(marker, addPrefix(s), throwable);
        }

        @Override
        public void warn(Marker marker, String s, Object o) {
            logger.warn(marker, addPrefix(s), o);
        }

        @Override
        public void info(Marker marker, String s) {
            logger.info(marker, addPrefix(s));
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return logger.isTraceEnabled(marker);
        }

        @Override
        public void info(Marker marker, String s, Object o) {
            logger.info(marker, addPrefix(s), o);
        }

        @Override
        public void trace(String s, Object o) {
            logger.trace(addPrefix(s), o);
        }

        @Override
        public void info(String s, Throwable throwable) {
            logger.info(addPrefix(s), throwable);
        }

        @Override
        public void info(String s, Object o) {
            logger.info(addPrefix(s), o);
        }

        @Override
        public void error(String s, Object o, Object o1) {
            logger.error(addPrefix(s), o, o1);
        }

        @Override
        public void debug(Marker marker, String s, Object o, Object o1) {
            logger.debug(marker, addPrefix(s), o, o1);
        }

        @Override
        public void warn(String s, Object o) {
            logger.warn(addPrefix(s), o);
        }

        @Override
        public void info(String s) {
            logger.info(addPrefix(s));
        }

        @Override
        public void error(Marker marker, String s, Throwable throwable) {
            logger.error(marker, addPrefix(s), throwable);
        }

        @Override
        public boolean isInfoEnabled() {
            return logger.isInfoEnabled();
        }

        @Override
        public void debug(Marker marker, String s) {
            logger.debug(marker, addPrefix(s));
        }

        @Override
        public void error(Marker marker, String s, Object o) {
            logger.error(marker, addPrefix(s), o);
        }

        @Override
        public boolean isErrorEnabled() {
            return logger.isErrorEnabled();
        }

        @Override
        public void trace(String s, Throwable throwable) {
            logger.trace(addPrefix(s), throwable);
        }

        @Override
        public void debug(String s, Object o) {
            logger.debug(addPrefix(s), o);
        }

        @Override
        public void error(String s, Object o) {
            logger.error(addPrefix(s), o);
        }

        @Override
        public void debug(String s, Throwable throwable) {
            logger.debug(addPrefix(s), throwable);
        }

        @Override
        public void trace(Marker marker, String s, Object o) {
            logger.trace(marker, addPrefix(s), o);
        }

        @Override
        public void trace(Marker marker, String s) {
            logger.trace(marker, addPrefix(s));
        }

        @Override
        public void info(Marker marker, String s, Object o, Object o1) {
            logger.info(marker, addPrefix(s), o, o1);
        }

        @Override
        public void warn(Marker marker, String s, Object... objects) {
            logger.warn(marker, addPrefix(s), objects);
        }

        @Override
        public void debug(String s, Object o, Object o1) {
            logger.debug(addPrefix(s), o, o1);
        }

        @Override
        public void trace(String s, Object... objects) {
            logger.trace(addPrefix(s), objects);
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return logger.isWarnEnabled(marker);
        }

        @Override
        public void warn(Marker marker, String s) {
            logger.warn(marker, addPrefix(s));
        }

        @Override
        public void error(String s) {
            logger.error(addPrefix(s));
        }

        @Override
        public void debug(String s) {
            logger.debug(addPrefix(s));
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        @Override
        public void info(Marker marker, String s, Object... objects) {
            logger.info(marker, addPrefix(s), objects);
        }

        @Override
        public void error(String s, Object... objects) {
            logger.error(addPrefix(s), objects);
        }

        @Override
        public void debug(Marker marker, String s, Throwable throwable) {
            logger.debug(marker, addPrefix(s), throwable);
        }

        @Override
        public void warn(Marker marker, String s, Object o, Object o1) {
            logger.warn(marker, addPrefix(s), o, o1);
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return logger.isErrorEnabled(marker);
        }

        @Override
        public void trace(Marker marker, String s, Throwable throwable) {
            logger.trace(marker, addPrefix(s), throwable);
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isWarnEnabled();
        }

        @Override
        public void trace(String s, Object o, Object o1) {
            logger.trace(addPrefix(s), o, o1);
        }

        @Override
        public void trace(Marker marker, String s, Object... objects) {
            logger.trace(marker, addPrefix(s), objects);
        }

        @Override
        public void warn(String s) {
            logger.warn(addPrefix(s));
        }

        @Override
        public void trace(String s) {
            logger.trace(addPrefix(s));
        }

        @Override
        public void debug(Marker marker, String s, Object o) {
            logger.debug(marker, addPrefix(s), o);
        }

        @Override
        public void warn(String s, Object... objects) {
            logger.warn(addPrefix(s), objects);
        }
    }
}
