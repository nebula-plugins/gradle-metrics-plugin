/*
 *  Copyright 2015-2019 Netflix, Inc.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String SANITIZED = "SANITIZED";

    public static Info create(GradleToolContainer tool) {
        return create(tool, new UnknownTool(), new UnknownTool());
    }

    public static Info create(Tool tool, Tool scm, Tool ci) {
        return create(tool, scm, ci, System.getenv(), new HashMap(System.getProperties()));
    }

    public static Info create(Tool tool, Tool scm, Tool ci, Map<String, String> env, Map<String, String> systemProperties) {
        return new Info(tool, scm, ci, env, systemProperties);
    }

    public static Info sanitize(Info info, List<String> sanitizedProperties, String sanitizedPropertiesRegex) {
        Pattern sanitizedPropertiesPattern = Pattern.compile(sanitizedPropertiesRegex);
        return new Info(
                info.getBuild(),
                info.getScm(),
                info.getCi(),
                sanitizeMap(info.getEnvironmentVariables(), sanitizedProperties, sanitizedPropertiesPattern),
                sanitizeMap(info.getSystemProperties(), sanitizedProperties, sanitizedPropertiesPattern)
        );
    }

    private static ConcurrentMap<String, String> sanitizeMap(Map<String, String> map, List<String> sanitizedProperties, Pattern sanitizedPropertiesPattern) {
        ConcurrentHashMap<String, String> sanitizedMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            Matcher m = sanitizedPropertiesPattern.matcher(entry.getKey());
            if (sanitizedProperties.contains(entry.getKey()) || m.matches()) {
                sanitizedMap.put(entry.getKey(), SANITIZED);
            } else {
                sanitizedMap.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitizedMap;
    }

    @NonNull
    private Tool build;

    @NonNull
    private Tool scm;

    @NonNull
    private Tool ci;

    @NonNull
    private Map<String, String> environmentVariables;

    @NonNull
    private Map<String, String> systemProperties;

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
        return systemProperties.getOrDefault(propertyName, EMPTY_STRING);
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

