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

package nebula.plugin.metrics.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Environment.
 */
@Value
@JsonPropertyOrder({"build", "scm", "ci", "environmentVariables", "systemProperties", "javaVersion", "detailedJavaVersion"})
public class Info {
    private static final String UNKNOWN = "UNKNOWN";
    private static final String OPEN_JDK = "OpenJDK";
    private static final String ORACLE_JDK = "Oracle";
    private static final String SE_RUNTIME_ENVIRONMENT = "Java(TM) SE Runtime Environment";
    private static final String HOTSPOT = "HotSpot(TM)";
    private static final String EMPTY_STRING = "";
    private static final String SPACE = " ";

    public static Info create(GradleToolContainer tool) {
        return create(tool, new UnknownTool(), new UnknownTool());
    }

    public static Info create(Tool tool, Tool scm, Tool ci) {
        return create(tool, scm, ci, System.getenv(), new HashMap(System.getProperties()));
    }

    public static Info create(Tool tool, Tool scm, Tool ci, Map<String, String> env, Map<String, String> systemProperties) {
        List<KeyValue> envList = KeyValue.mapToKeyValueList(env);
        List<KeyValue> systemPropertiesList = KeyValue.mapToKeyValueList(systemProperties);
        return new Info(tool, scm, ci, envList, systemPropertiesList);
    }

    public static Info sanitize(Info info, List<String> sanitizedProperties) {
        List<KeyValue> systemProperties = sanitizeKeyValues(info.getSystemProperties(), sanitizedProperties);
        List<KeyValue> environmentVariables = sanitizeKeyValues(info.getEnvironmentVariables(), sanitizedProperties);
        return new Info(info.getBuild(), info.getScm(), info.getCi(), environmentVariables, systemProperties);
    }

    private static List<KeyValue> sanitizeKeyValues(List<KeyValue> keyValues, List<String> sanitizedProperties) {
        List<KeyValue> sanitizedKeyValues = new ArrayList<>();
        for (KeyValue keyValue : keyValues) {
            if (sanitizedProperties.contains(keyValue.getKey())) {
                sanitizedKeyValues.add(new KeyValue(keyValue.getKey(), "SANITIZED"));
            } else {
                sanitizedKeyValues.add(keyValue);
            }
        }
        return sanitizedKeyValues;
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
        String javaVersion = findProperty("java.version");
        return !javaVersion.isEmpty() ? javaVersion : "unknown";
    }

    public String getDetailedJavaVersion() {
        String javaRuntimeName = findProperty("java.runtime.name");
        String javaVersion = findProperty("java.version");
        String javaVendor = findProperty("java.vm.vendor");
        return determineJavaVersion(javaRuntimeName, javaVersion, javaVendor);
    }

    private String findProperty(String propertyName) {
        for (KeyValue systemProperty : systemProperties) {
            if (systemProperty.getKey().equals(propertyName)) {
                return systemProperty.getValue();
            }
        }
        return EMPTY_STRING;
    }

    private String determineJavaVersion(String javaRuntimeName, String javaVersion, String javaVendor) {
        if(javaVersion.isEmpty()) {
          return UNKNOWN.toLowerCase();
        }

        String javaRuntimeNameLowerCase = javaRuntimeName.toLowerCase();
        if(javaRuntimeName.toLowerCase().contains(OPEN_JDK.toLowerCase())) {
            String version = OPEN_JDK.concat(SPACE).concat(javaVersion);
            if(javaVendor.toLowerCase().contains("azul")) {
                version = version.concat("-zulu");
            }
            return version;
        } else if(javaRuntimeNameLowerCase.contains(SE_RUNTIME_ENVIRONMENT.toLowerCase()) || javaRuntimeNameLowerCase.contains(HOTSPOT.toLowerCase()) || javaRuntimeNameLowerCase.contains(ORACLE_JDK.toLowerCase())) {
            return ORACLE_JDK.concat(SPACE).concat(javaVersion);
        } else {
            return UNKNOWN.concat(SPACE).concat(javaVersion);
        }
    }
}
