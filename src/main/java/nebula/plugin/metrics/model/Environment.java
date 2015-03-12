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

import autovalue.shaded.com.google.common.common.collect.Lists;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Environment.
 */
@AutoValue
@JsonPropertyOrder({"build", "scm", "ci", "environmentVariables", "systemProperties"})
public abstract class Environment {
    public static Environment create(Tool tool, SCM scm, CI ci) {
        return create(tool, scm, ci, System.getenv(), new HashMap(System.getProperties()));
    }

    public static Environment create(Tool tool, SCM scm, CI ci, Map<String, String> env, Map<String, String> systemProperties) {
        // TODO do we need to blacklist/redact certain properties?
        List<KeyValue> envList = mapToKeyValueList(env);
        List<KeyValue> systemPropertiesList = mapToKeyValueList(systemProperties);
        return new AutoValue_Environment(tool, scm, ci, envList, systemPropertiesList);
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
        return KeyValue.create(key, value);
    }

    public abstract Tool getBuild();

    public abstract SCM getSCM();

    public abstract CI getCI();

    public abstract List<KeyValue> getEnvironmentVariables();

    public abstract List<KeyValue> getSystemProperties();
}
