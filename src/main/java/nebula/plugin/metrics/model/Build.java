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

package nebula.plugin.metrics.model;

import com.google.common.collect.ImmutableList;
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
    private Info info;
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

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = checkNotNull(info);
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
