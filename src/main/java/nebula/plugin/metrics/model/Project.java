package nebula.plugin.metrics.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.auto.value.AutoValue;

/**
 * Project.
 *
 * @author Danny Thomas
 */
@AutoValue
public abstract class Project {
    public static Project create(String name, String version) {
        return new AutoValue_Project(name, version);
    }

    public abstract String getName();

    public abstract String getVersion();
}
