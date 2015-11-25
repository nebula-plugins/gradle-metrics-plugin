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

package nebula.plugin.metrics

import nebula.test.ProjectSpec
import org.gradle.api.internal.plugins.PluginApplicationException

class MetricsPluginTest extends ProjectSpec {
    def 'plugin can only be applied to root project'() {
        given:
        def subproject = addSubproject('subproject')

        when:
        subproject.plugins.apply(MetricsPlugin)

        then:
        thrown(PluginApplicationException)
    }
}
