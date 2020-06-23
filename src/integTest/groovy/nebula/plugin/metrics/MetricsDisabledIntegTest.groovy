/*
 *  Copyright 2015-2020 Netflix, Inc.
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

package nebula.plugin.metrics
import nebula.test.IntegrationSpec

class MetricsDisabledIntegTest extends IntegrationSpec {

    def setup() {
        fork = false
    }

    def 'Gradle metrics are disabled if metrics.enabled is false - project plugin'() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: ${MetricsPlugin.name}

            metrics {
                dispatcherType = 'NOOP'
            }

            repositories {
               mavenCentral()
            }

            dependencies {
               testImplementation 'junit:junit:4.11'
            }
        """
        when:
        def result = runTasksSuccessfully('build', '-Pmetrics.enabled=false')

        then:
        result.standardOutput.contains('Metrics have been disabled for this build.')
        !result.standardOutput.contains('[metrics] Shutting down dispatcher')
    }

    def 'Gradle metrics are disabled if metrics.enabled is false - init plugin'() {
        setup:
        File init = new File(projectDir, "init.gradle")
        init.text = """
            initscript {
                dependencies {
                   ${DependenciesBuilderWithClassesUnderTest.buildDependencies()}
                }
            }

            apply plugin: nebula.plugin.metrics.MetricsInitPlugin
""".stripMargin()
        addInitScript(init)
        buildFile << """
            apply plugin: 'java'

            metrics {
                dispatcherType = 'NOOP'
            }
        """

        when:
        def result = runTasksSuccessfully('build', '-Pmetrics.enabled=false')

        then:
        result.standardOutput.contains('Metrics have been disabled for this build.')
        !result.standardOutput.contains('[metrics] Shutting down dispatcher')
    }

    def 'Gradle metrics are disabled if metrics.enabled is false - init plugin + gradle.properties'() {
        setup:
        File gradleProperties = new File(projectDir, "gradle.properties")
        gradleProperties.text = '''
metrics.enabled=false
'''
        File init = new File(projectDir, "init.gradle")
        init.text = """
            initscript {
                dependencies {
                   ${DependenciesBuilderWithClassesUnderTest.buildDependencies()}
                }
            }

            apply plugin: nebula.plugin.metrics.MetricsInitPlugin
""".stripMargin()
        addInitScript(init)
        buildFile << """
            apply plugin: 'java'

            metrics {
                dispatcherType = 'NOOP'
            }
        """

        when:
        def result = runTasksSuccessfully('build')

        then:
        result.standardOutput.contains('Metrics have been disabled for this build.')
        !result.standardOutput.contains('[metrics] Shutting down dispatcher')
    }
}
