package nebula.plugin.metrics;

import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;

/**
 * Nebula build metrics plugin extension.
 *
 * @author Danny Thomas
 */
public class MetricsPluginExtension {
    private static final Integer DEFAULT_TIMEOUT = 5000;

    /**
     * The name used when adding this extension to the extension container.
     */
    public static final String METRICS_EXTENSION_NAME = "metrics";

    /**
     * Retrieve the metrics extension for the root project.
     *
     * @param gradle the {@link org.gradle.api.invocation.Gradle} instance to retrieve the root project from
     * @return the {@link MetricsPluginExtension} for the root project
     */
    public static MetricsPluginExtension getRootMetricsExtension(Gradle gradle) {
        ExtensionContainer extensions = gradle.getRootProject().getExtensions();
        return (MetricsPluginExtension) extensions.findByName(METRICS_EXTENSION_NAME);
    }

    private String hostname = "localhost";
    private int port = 9300;
    private String clusterName = "elasticsearch";
    private int connectTimeout = DEFAULT_TIMEOUT;
    private int socketTimeout = DEFAULT_TIMEOUT;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
}
