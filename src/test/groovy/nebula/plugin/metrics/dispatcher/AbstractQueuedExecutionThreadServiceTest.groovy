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

package nebula.plugin.metrics.dispatcher

import spock.lang.Specification

import java.util.concurrent.LinkedBlockingQueue

/**
 * Tests for {@link AbstractQueuedExecutionThreadService}.
 */
class AbstractQueuedExecutionThreadServiceTest extends Specification {
    def service = new SimpleQueuedExecutionThreadService()

    def 'dummy'() {
        expect:
        true
    }

    private static class SimpleQueuedExecutionThreadService extends AbstractQueuedExecutionThreadService {
        def SimpleQueuedExecutionThreadService() {
            super(new LinkedBlockingQueue<>(), false)
        }

        @Override
        protected void execute(Object action) throws Exception {
        }
    }
}
