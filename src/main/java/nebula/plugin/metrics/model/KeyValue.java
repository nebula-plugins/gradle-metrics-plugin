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

import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Key/value.
 */
@Value
public class KeyValue {
    @NonNull
    private String key;

    @NonNull
    private String value;

    static List<KeyValue> mapToKeyValueList(Map<String, String> map) {
        Set<Map.Entry<String, String>> entries = map.entrySet();
        List<KeyValue> keyValues = Lists.newArrayListWithCapacity(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            keyValues.add(entryToKeyValue(entry));
        }
        return keyValues;
    }

    static KeyValue entryToKeyValue(Map.Entry<String, String> entry) {
        String key = String.valueOf(entry.getKey());
        String value = String.valueOf(entry.getValue());
        return new KeyValue(key, value);
    }
}
