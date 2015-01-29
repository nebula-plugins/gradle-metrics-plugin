/*
 * Copyright 2013-2015 the original author or authors.
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

package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.CI;
import nebula.plugin.metrics.model.Environment;
import nebula.plugin.metrics.model.Gradle;
import nebula.plugin.metrics.model.SCM;

import org.gradle.StartParameter;

/**
 * Collector for Gradle {@link StartParameter}.
 */
public class GradleStartParameterCollector {
    public static void collect(StartParameter startParameter, MetricsDispatcher dispatcher) {
        Gradle tool = new Gradle(startParameter);
        SCM scm = SCM.detect();
        CI ci = CI.detect();
        dispatcher.environment(Environment.create(tool, scm, ci));
    }
}
