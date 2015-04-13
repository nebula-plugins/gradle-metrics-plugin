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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Environment.
 */
@Value
@JsonPropertyOrder({"build", "scm", "ci", "environmentVariables", "systemProperties"})
public class Info {
    public static Info create(Tool tool, SCM scm, CI ci) {
        return create(tool, scm, ci, System.getenv(), new HashMap(System.getProperties()));
    }

    public static Info create(Tool tool, SCM scm, CI ci, Map<String, String> env, Map<String, String> systemProperties) {
        // TODO do we need to blacklist/redact certain properties?
        List<KeyValue> envList = mapToKeyValueList(env);
        List<KeyValue> systemPropertiesList = mapToKeyValueList(systemProperties);
        return new Info(tool, scm, ci, envList, systemPropertiesList);
    }

    @VisibleForTesting
    static List<KeyValue> mapToKeyValueList(Map<String, String> map) {
        Set<Map.Entry<String, String>> entries = map.entrySet();
        List<KeyValue> keyValues = Lists.newArrayListWithCapacity(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            keyValues.add(entryToKeyValue(entry));
        }
        return keyValues;
    }

    @VisibleForTesting
    static KeyValue entryToKeyValue(Map.Entry<String, String> entry) {
        String key = String.valueOf(entry.getKey());
        String value = String.valueOf(entry.getValue());
        return new KeyValue(key, value);
    }

    @NonNull
    private Tool build;

    @NonNull
    private SCM scm;

    @NonNull
    private CI ci;

    @NonNull
    private List<KeyValue> environmentVariables;

    @NonNull
    private List<KeyValue> systemProperties;
}
