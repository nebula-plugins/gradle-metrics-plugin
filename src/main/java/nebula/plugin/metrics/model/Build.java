package nebula.plugin.metrics.model;

import autovalue.shaded.com.google.common.common.collect.ImmutableList;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Build.
 *
 * @author Danny Thomas
 */
public class Build {
    private Project project;
    private final List<Event> events = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<Test> tests = new ArrayList<>();
    private final List<Artifact> artifacts = new ArrayList<>();
    private Environment environment;
    private Result result = Result.unknown();
    private long startTime;
    private long elapsedTime;

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = checkNotNull(project);
    }

    public List<Event> getEvents() {
        return ImmutableList.copyOf(events);
    }

    public void addEvent(Event event) {
        events.add(checkNotNull(event));
    }

    public List<Task> getTasks() {
        return ImmutableList.copyOf(tasks);
    }

    public void addTask(Task task) {
        tasks.add(checkNotNull(task));
    }

    public List<Test> getTests() {
        return ImmutableList.copyOf(tests);
    }

    public void addTest(Test test) {
        tests.add(checkNotNull(test));
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = checkNotNull(environment);
    }

    public List<Artifact> getArtifacts() {
        return ImmutableList.copyOf(artifacts);
    }

    public void addArtifact(Artifact artifact) {
        artifacts.add(checkNotNull(artifact));
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = checkNotNull(result);
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public DateTime getStartTime() {
        return new DateTime(startTime);
    }

    public DateTime getFinishedTime() {
        return new DateTime(startTime + elapsedTime);
    }

    public void setElapsedTime(long elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public Long getElapsedTime() {
        return elapsedTime;
    }
}
