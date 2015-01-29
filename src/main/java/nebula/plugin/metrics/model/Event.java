package nebula.plugin.metrics.model;

import com.google.auto.value.AutoValue;

/**
 * Value class representing a build event.
 */
@AutoValue
public abstract class Event {
    public static Event create(String description, String type, long elapsedTime) {
        return new AutoValue_Event(description, type, elapsedTime);
    }

    public abstract String getDescription();

    public abstract String getType();

    public abstract long getElapsedTime();
}
