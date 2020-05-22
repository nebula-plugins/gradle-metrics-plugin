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

package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.model.GenericCI;
import nebula.plugin.metrics.model.GenericSCM;
import nebula.plugin.metrics.model.GenericToolContainer;
import nebula.plugin.metrics.model.Tool;

import org.gradle.api.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector for Gradle info.
 */
public class GradleInfoCollector {
    private final Plugin plugin;

    public GradleInfoCollector(Plugin plugin) {
        this.plugin = checkNotNull(plugin);
    }

    public Tool getSCM() {
        Map<String, String> manifest = buildManifest();
        GenericSCM scm = new GenericSCM(manifest.get("Module-Origin"), manifest.get("Change"));
        return new GenericToolContainer(scm);
    }

    public Tool getCI() {
        Map<String, String> manifest = buildManifest();
        GenericCI ci = new GenericCI(manifest.get("Build-Number"), manifest.get("Build-Job"), manifest.get("Build-Host"), manifest.get("Built-By"), manifest.get("Build-OS"));
        return new GenericToolContainer(ci);
    }

    private Map<String, String> buildManifest() {
        try {
            Method method = plugin.getClass().getDeclaredMethod("buildManifest");
            return (Map<String, String>) method.invoke(plugin);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
