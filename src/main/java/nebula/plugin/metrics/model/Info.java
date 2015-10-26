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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.NonNull;
import lombok.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Environment.
 */
@Value
@JsonPropertyOrder({"build", "scm", "ci", "environmentVariables", "systemProperties"})
public class Info {
    public static Info create(GradleToolContainer tool) {
        return create(tool, new UnknownTool(), new UnknownTool());
    }

    public static Info create(Tool tool, Tool scm, Tool ci) {
        return create(tool, scm, ci, System.getenv(), new HashMap(System.getProperties()));
    }

    public static Info create(Tool tool, Tool scm, Tool ci, Map<String, String> env, Map<String, String> systemProperties) {
        // TODO do we need to blacklist/redact certain properties?
        List<KeyValue> envList = KeyValue.mapToKeyValueList(env);
        List<KeyValue> systemPropertiesList = KeyValue.mapToKeyValueList(systemProperties);
        return new Info(tool, scm, ci, envList, systemPropertiesList);
    }

    @NonNull
    private Tool build;

    @NonNull
    private Tool scm;

    @NonNull
    private Tool ci;

    @NonNull
    private List<KeyValue> environmentVariables;

    @NonNull
    private List<KeyValue> systemProperties;

    public String getJavaVersion() {
        for (KeyValue systemProperty : systemProperties) {
            if (systemProperty.getKey().equals("java.version")) {
                return systemProperty.getValue();
            }
        }
        return "unknown";
    }
}
