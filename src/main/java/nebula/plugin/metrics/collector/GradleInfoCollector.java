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

package nebula.plugin.metrics.collector;

import nebula.plugin.info.InfoBrokerPlugin;
import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.model.CI;
import nebula.plugin.metrics.model.GenericCI;
import nebula.plugin.metrics.model.GenericSCM;
import nebula.plugin.metrics.model.SCM;

import org.gradle.api.Plugin;
import org.slf4j.Logger;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector for Gradle info.
 */
public class GradleInfoCollector {
    private final InfoBrokerPlugin plugin;

    public GradleInfoCollector(Plugin plugin) {
        this.plugin = (InfoBrokerPlugin) checkNotNull(plugin);
    }

    public SCM getSCM() {
        Map<String, String> manifest = plugin.buildManifest();
        return new GenericSCM(manifest.get("Module-Origin"), manifest.get("Change"));
    }

    public CI getCI() {
        Map<String, String> manifest = plugin.buildManifest();
        return new GenericCI(manifest.get("Build-Number"), manifest.get("Build-Job"), manifest.get("Build-Host"), manifest.get("Built-By"), manifest.get("Build-OS"));
    }
}
