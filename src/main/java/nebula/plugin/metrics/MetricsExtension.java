package nebula.plugin.metrics;

import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;

/**
 * Nebula build metrics plugin extension.
 *
 * @author Danny Thomas
 */
public class MetricsExtension {
    /**
     * The name used when adding this extension to the extension container.
     */
    public static final String METRICS_EXTENSION_NAME = "metrics";

    /**
     * Retrieve the metrics extension for the root project.
     *
     * @param gradle the {@link org.gradle.api.invocation.Gradle} instance to retrieve the root project from
     * @return the {@link nebula.plugin.metrics.MetricsExtension} for the root project
     */
    public static MetricsExtension getRootMetricsExtension(Gradle gradle) {
        ExtensionContainer extensions = gradle.getRootProject().getExtensions();
        return (MetricsExtension) extensions.findByName(METRICS_EXTENSION_NAME);
    }
}
