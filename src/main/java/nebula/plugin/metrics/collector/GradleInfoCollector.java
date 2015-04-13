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
import nebula.plugin.metrics.model.*;

import org.gradle.api.Plugin;
import org.slf4j.Logger;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector for Gradle info.
 */
public class GradleInfoCollector {
    private final Logger logger = MetricsLoggerFactory.getLogger(GradleInfoCollector.class);
    private final InfoBrokerPlugin plugin;

    public GradleInfoCollector(Plugin plugin) {
        this.plugin = (InfoBrokerPlugin) checkNotNull(plugin);
    }

    public SCM getSCM() {
        // TODO Implement
        /*
        Module-Source=
        Module-Origin=git@github.com:nebula-plugins/nebula-test.git
        Change=3e5440a
         */
        Map<String, String> manifest = plugin.buildManifest();
        return new GenericSCM();
    }

    public CI getCI() {
        // TODO Complete implementation
        /*
        Built-Status=release
        Built-By=jenkins
        Built-OS=Linux
        Build-Date=2014-11-25_14:40:33
        Build-Host=https://netflixoss.ci.cloudbees.com/
        Build-Job=nebula-plugins/nebula-test-1.12-release
        Build-Number=9
        Build-Id=2014-11-25_22-33-51
        Build-Java-Version=1.7.0_60
         */

        Map<String, String> manifest = plugin.buildManifest();
        return new GenericCI();
    }
}
