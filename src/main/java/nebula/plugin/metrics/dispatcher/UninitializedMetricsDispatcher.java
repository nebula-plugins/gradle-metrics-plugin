/*
 *  Copyright 2015-2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.model.*;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Service;
import org.gradle.internal.logging.events.LogEvent;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An uninitialised dispatcher to act as a placeholder until the plugin extension has been configured.
 */
public class UninitializedMetricsDispatcher implements MetricsDispatcher {
    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("The dispatcher has not been initialised");
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public State state() {
        return State.NEW;
    }

    @Override
    public void started(Project project) {
        throw unsupported();
    }


    @Override
    public void duration(long startTime, long elapsedTime) {
        throw unsupported();
    }

    @Override
    public void result(Result result) {
        throw unsupported();
    }

    @Override
    public void event(String description, String type, long elapsedTime) {
        throw unsupported();
    }

    @Override
    public void task(Task task) {
        throw unsupported();
    }

    @Override
    public void logEvent(LogEvent event) {
        throw unsupported();
    }

    @Override
    public void logEvents(Collection<LogEvent> events) {
        throw unsupported();
    }

    @Override
    public void test(Test test) {
        throw unsupported();
    }

    @Override
    public void environment(Info info) {
        throw unsupported();
    }

    @Override
    public void report(String reportName, Object report) {
        throw unsupported();
    }

    @Override
    public Optional<String> receipt() {
        throw unsupported();
    }

    @Override
    public Service startAsync() {
        throw unsupported();
    }

    @Override
    public Service stopAsync() {
        throw unsupported();
    }

    @Override
    public void awaitRunning() {
        throw unsupported();
    }

    @Override
    public void awaitRunning(long timeout, TimeUnit unit) throws TimeoutException {
        throw unsupported();
    }

    @Override
    public void awaitTerminated() {
        throw unsupported();
    }

    @Override
    public void awaitTerminated(long timeout, TimeUnit unit) throws TimeoutException {
        throw unsupported();
    }

    @Override
    public Throwable failureCause() {
        throw unsupported();
    }

    @Override
    public void addListener(Listener listener, Executor executor) {
        throw unsupported();
    }
}
