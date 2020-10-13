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
import lombok.Data;
import lombok.NonNull;
import lombok.Value;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Environment.
 */
@Value
@JsonPropertyOrder({"build", "scm", "ci", "environmentVariables", "systemProperties", "javaVersion", "detailedJavaVersion", "nebulaFeatures"})
public class Info {
    private static final String UNKNOWN = "UNKNOWN";
    private static final String OPEN_JDK = "OpenJDK";
    private static final String ORACLE_JDK = "Oracle";
    private static final String SE_RUNTIME_ENVIRONMENT = "Java(TM) SE Runtime Environment";
    private static final String HOTSPOT = "HotSpot(TM)";
    private static final String EMPTY_STRING = "";
    private static final String SPACE = " ";
    private static final String SANITIZED = "SANITIZED";

    public static Info create(GradleToolContainer tool, org.gradle.api.Project gradleProject) {
        return create(tool, new UnknownTool(), new UnknownTool(), gradleProject);
    }

    public static Info create(Tool tool, Tool scm, Tool ci, org.gradle.api.Project gradleProject) {
        Map<String, String> systemProps = new HashMap(System.getProperties());
        return create(tool, scm, ci, System.getenv(), systemProps, getNebulaFeatures(gradleProject, systemProps));
    }

    public static Info create(Tool tool, Tool scm, Tool ci, Map<String, String> env, Map<String, String> systemProperties, Map<String, String> nebulaFeatures) {
        List<KeyValue> envList = KeyValue.mapToKeyValueList(env);
        List<KeyValue> systemPropertiesList = KeyValue.mapToKeyValueList(systemProperties);
        List<KeyValue> nebulaFeaturesList = KeyValue.mapToKeyValueList(nebulaFeatures);
        String javaRuntimeName = systemProperties.get("java.runtime.name");
        String javaVersion = systemProperties.get("java.version");
        String javaVendor = systemProperties.get("java.vm.vendor");
        String detailedJavaVersion = determineJavaVersion(javaRuntimeName, javaVersion, javaVendor);
        return new Info(tool, scm, ci, envList, systemPropertiesList, nebulaFeaturesList, javaVersion.isEmpty() ? "unknown" : javaVersion, detailedJavaVersion);
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

    @NonNull
    private List<KeyValue> nebulaFeatures;

    @NonNull
    private String javaVersion;

    @NonNull
    private String detailedJavaVersion;

    public static String determineJavaVersion(String javaRuntimeName, String javaVersion, String javaVendor) {
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

    public static Info sanitize(Info info, List<String> sanitizedProperties, String sanitizedPropertiesRegex) {
        Pattern sanitizedPropertiesPattern = Pattern.compile(sanitizedPropertiesRegex);
        return new Info(info.getBuild(), info.getScm(), info.getCi(),
                sanitizeKeyValues(info.getEnvironmentVariables(), sanitizedProperties, sanitizedPropertiesPattern),
                sanitizeKeyValues(info.getSystemProperties(), sanitizedProperties, sanitizedPropertiesPattern),
                sanitizeKeyValues(info.getNebulaFeatures(), sanitizedProperties, sanitizedPropertiesPattern),
                info.getJavaVersion(),
                info.getDetailedJavaVersion());
    }

    private static List<KeyValue> sanitizeKeyValues(List<KeyValue> keyValues, List<String> sanitizedProperties, Pattern sanitizedPropertiesPattern) {
        List<KeyValue> sanitizedKeyValues = new ArrayList<>();
        for (KeyValue keyValue : keyValues) {
            Matcher m = sanitizedPropertiesPattern.matcher(keyValue.getKey());
            if (sanitizedProperties.contains(keyValue.getKey()) || m.matches()) {
                sanitizedKeyValues.add(new KeyValue(keyValue.getKey(), SANITIZED));
            } else {
                sanitizedKeyValues.add(keyValue);
            }
        }
        return sanitizedKeyValues;
    }

    private static Map<String, String> getNebulaFeatures(org.gradle.api.Project gradleProject,  Map<String, String> systemProperties) {
        Map<String, String> nebulaFeatures = new HashMap<>();
        List<Map.Entry<String, ?>> nebulaFeaturesFromProjectProperties = extractNebulaFeatures(gradleProject.getProperties());
        List<Map.Entry<String, ?>> nebulaFeaturesFromSystemProperties = extractNebulaFeatures(systemProperties);
        nebulaFeaturesFromProjectProperties.forEach(stringEntry -> nebulaFeatures.put(stringEntry.getKey(), stringEntry.getValue().toString()));
        nebulaFeaturesFromSystemProperties.forEach(stringEntry -> nebulaFeatures.put(stringEntry.getKey(), stringEntry.getValue().toString()));
        return nebulaFeatures;
    }

    private static  List<Map.Entry<String, ?>> extractNebulaFeatures(Map<String, ?> properties) {
        return properties.entrySet().stream().filter((Predicate<Map.Entry<String, ?>>) stringEntry -> stringEntry.getKey().startsWith("nebula.feature")).collect(Collectors.toList());
    }
}
