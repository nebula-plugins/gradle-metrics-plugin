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

package nebula.plugin.metrics;

import org.gradle.api.logging.LogLevel;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Nebula build metrics plugin extension.
 *
 * @author Danny Thomas
 */
public class MetricsPluginExtension {
    /**
     * The name used when adding this extension to the extension container.
     */
    public static final String METRICS_EXTENSION_NAME = "metrics";
    private static final String INDEX_PREFIX = "build-metrics-";
    private static final String LOGSTASH_INDEX_PREFIX = "logstash-";
    public static final DateTimeFormatter ROLLING_FORMATTER = DateTimeFormat.forPattern("yyyyMM");
    private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.WARN;

    public static final String DEFAULT_INDEX_NAME = INDEX_PREFIX + "default";

    private String hostname = "localhost";
    private int transportPort = 9300;
    private int httpPort = 9200;
    private String clusterName = "elasticsearch";
    private String indexName = DEFAULT_INDEX_NAME;
    private LogLevel logLevel = DEFAULT_LOG_LEVEL;

    private int suroPort = 443;
    private boolean suroHttps = true;
    private String suroBuildEventName = "build_metrics";
    private String suroLogEventName = "build_metrics_events";

    private DispatcherType dispatcherType = DispatcherType.ES_HTTP;
    private List<String> sanitizedProperties = new ArrayList<>();

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = checkNotNull(hostname);
    }

    public int getTransportPort() {
        return transportPort;
    }

    public void setTransportPort(int transportPort) {
        this.transportPort = transportPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = checkNotNull(clusterName);
    }

    public String getIndexName() {
        return indexName;
    }

    public String getLogstashIndexName() {
        String rollingSuffix = "-" + ROLLING_FORMATTER.print(DateTime.now());
        return LOGSTASH_INDEX_PREFIX + indexName + rollingSuffix;
    }

    public void setIndexName(String indexName) {
        this.indexName = INDEX_PREFIX + checkNotNull(indexName);
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = LogLevel.valueOf(logLevel.toUpperCase());
    }

    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }

    public void setDispatcherType(String dispatcherType) {
        this.dispatcherType = DispatcherType.valueOf(dispatcherType.toUpperCase());
    }

    public List<String> getSanitizedProperties() {
        return sanitizedProperties;
    }

    public void setSanitizedProperties(List<String> sanitizedProperties) {
        this.sanitizedProperties = checkNotNull(sanitizedProperties);
    }

    public String getSuroLogEventName() {
        return suroLogEventName;
    }

    public void setSuroLogEventName(String suroLogEventName) {
        this.suroLogEventName = checkNotNull(suroLogEventName);
    }

    public String getSuroBuildEventName() {
        return suroBuildEventName;
    }

    public void setSuroBuildEventName(String suroBuildEventName) {
        this.suroBuildEventName = checkNotNull(suroBuildEventName);
    }

    public int getSuroPort() {
        return suroPort;
    }

    public void setSuroPort(int suroPort) {
        this.suroPort = suroPort;
    }

    public boolean isSuroHttps() {
        return suroHttps;
    }

    public void setSuroHttps(boolean suroHttps) {
        this.suroHttps = suroHttps;
    }

    public enum DispatcherType {
        ES_CLIENT,
        ES_HTTP,
        SURO_REST,
        NOOP
    }
}
