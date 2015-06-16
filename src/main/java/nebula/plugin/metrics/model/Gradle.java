package nebula.plugin.metrics.model;

import lombok.NonNull;
import lombok.Value;

/**
 * Gradle.
 */
@Value
public class Gradle {
    @NonNull
    private String version;

    @NonNull
    private GradleParameters parameters;
}
