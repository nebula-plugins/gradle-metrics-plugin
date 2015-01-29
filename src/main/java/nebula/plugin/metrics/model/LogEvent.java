package nebula.plugin.metrics.model;

import com.google.auto.value.AutoValue;

/**
 * Logging event value class.
 *
 * @author Danny Thomas
 */
@AutoValue
public abstract class LogEvent {
    public static LogEvent create(String message) {
        return new AutoValue_LogEvent(message);
    }

    public abstract String getMessage();
}
