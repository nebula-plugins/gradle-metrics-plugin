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

package nebula.plugin.metrics

import nebula.test.ProjectSpec

/*
 * Tests for {@link MetricsPlugin}.
 */
class MetricsPluginTest extends ProjectSpec {
    def 'applying plugin registers extension'() {
        when:
        project.plugins.apply(MetricsPlugin)

        then:
        project.extensions.findByName(MetricsPluginExtension.METRICS_EXTENSION_NAME)
    }

    def 'plugin can only be applied to root project'() {
        given:
        def subproject = addSubproject('subproject')

        when:
        subproject.plugins.apply(MetricsPlugin)

        then:
        thrown(IllegalStateException)
    }

    def 'project evaluation succeeds'() {
        // Ideally, I'd check that all of the listeners we expect have been registered here, but unfortunately Gradle gives us no way at getting at those
        when:
        project.plugins.apply(MetricsPlugin)
        project.evaluate()

        then:
        noExceptionThrown()
    }
}
