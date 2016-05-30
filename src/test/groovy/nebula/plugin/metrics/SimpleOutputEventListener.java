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

package nebula.plugin.metrics;

import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An {@link OutputEventListener} that captures logging events to a {@link List}.
 *
 * @author Danny Thomas
 */
public class SimpleOutputEventListener implements OutputEventListener {
    private List<LogEvent> logEvents = new CopyOnWriteArrayList<>();

    @Override
    public void onOutput(OutputEvent outputEvent) {
        checkNotNull(outputEvent);
        if (outputEvent instanceof LogEvent) {
            logEvents.add((LogEvent) outputEvent);
        }
    }

    public List<LogEvent> getLogEvents() {
        return logEvents;
    }
}
