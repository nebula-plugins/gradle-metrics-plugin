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

package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.model.*;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Service;
import org.gradle.logging.internal.LogEvent;

import java.util.Collection;

/**
 * @author Danny Thomas
 */
public interface MetricsDispatcher extends Service {
    void started(Project project);

    void duration(long startTime, long elapsedTime);

    void result(Result result);

    void event(String description, String type, long elapsedTime);

    void task(Task task);

    void logEvent(LogEvent event);

    void logEvents(Collection<LogEvent> events);

    void test(Test test);

    void environment(Info info);

    Optional<String> receipt();
}
