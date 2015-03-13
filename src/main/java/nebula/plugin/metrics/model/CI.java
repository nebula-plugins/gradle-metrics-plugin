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

import com.google.auto.value.AutoValue;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Continuous integration.
 *
 * @author Danny Thomas
 */
@AutoValue
public abstract class CI implements Tool {
    public static CI detect() {
        return create(System.getenv());
    }

    public static CI create(Map<String, String> systemEnv) {
        checkNotNull(systemEnv);
        return new AutoValue_CI("unknown");
    }

    @Override
    public abstract String getType();
}
