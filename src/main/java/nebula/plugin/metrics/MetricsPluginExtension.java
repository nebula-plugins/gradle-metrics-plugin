package nebula.plugin.metrics;

import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;

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

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = checkNotNull(hostname);
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
        this.clusterName = checkNotNull(clusterName);
    }
}
