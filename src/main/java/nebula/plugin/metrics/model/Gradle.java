/*
 * Copyright 2015 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.gradle.StartParameter;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Gradle {@link Tool}.
 */
public final class Gradle implements Tool {
    private final StartParameter startParameter;

    public Gradle(StartParameter startParameter) {
        this.startParameter = checkNotNull(startParameter);
    }

    @Override
    public String getType() {
        return "gradle";
    }

    /**
     * We'll just use flat Gradle StartParameter for now. If we need more, we'll mix in this class with a subclass.
     */
    @JsonIgnoreProperties("mergedSystemProperties")
    // The StartParameter.getMergedSystemProperties() method has been deprecated and is scheduled to be removed in Gradle 2.0
    public StartParameter getGradle() {
        return startParameter;
    }
}
