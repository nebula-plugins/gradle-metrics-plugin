package nebula.plugin.metrics.model;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

/**
 * Task.
 *
 * @author Danny Thomas
 */
@AutoValue
public abstract class Task {
    public static Task create(String description, Result result, long startTime, long elapsedTime) {
        return new AutoValue_Task(description, result, new DateTime(startTime), elapsedTime);
    }

    public abstract String getDescription();

    public abstract Result getResult();

    public abstract DateTime getStartTime();

    public abstract long getElapsedTime();
}
