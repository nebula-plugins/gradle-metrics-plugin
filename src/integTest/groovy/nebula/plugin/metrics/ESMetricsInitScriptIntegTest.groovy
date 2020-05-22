package nebula.plugin.metrics

import groovy.util.logging.Slf4j
import nebula.test.IntegrationSpec
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Shared

@Slf4j
@Testcontainers
@IgnoreIf({ Boolean.valueOf(env["NEBULA_IGNORE_TEST"]) })
class ESMetricsInitScriptIntegTest extends IntegrationSpec {

    @Shared
    ElasticsearchContainer container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:5.4.1")

    def setup() {
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
        fork = false
    }

    def 'running projects task causes no errors and the build id to standard out'() {
        setup:
        setValidBuildFile(MetricsPluginExtension.DispatcherType.ES_HTTP)
        def result

        when:
        result = runTasksSuccessfully('build')

        then:
        noExceptionThrown()
        result.standardError.isEmpty()
        result.standardOutput.contains('Build id is ')
        getBuildId(result.standardOutput)
    }

    def 'running offline results in no metrics being recorded'() {
        setup:
        setValidBuildFile(MetricsPluginExtension.DispatcherType.ES_HTTP)
        def result

        when:
        result = runTasksSuccessfully("--offline", "build")

        then:
        noExceptionThrown()
        result.standardOutput.contains("Build is running offline")
    }


    def setValidBuildFile(MetricsPluginExtension.DispatcherType dispatcherType) {
        def build = """
        apply plugin: 'java'
        buildscript {
          repositories {
            maven {
              url "https://plugins.gradle.org/m2/"
            }
          }
          dependencies {
            classpath "com.netflix.nebula:gradle-info-plugin:7.1.4"
          }
        }
        
        apply plugin: "nebula.info"

        metrics {
            esBasicAuthUsername = 'elastic'
            esBasicAuthPassword = 'changeme'
            httpPort = ${container.firstMappedPort}
            transportPort = ${container.tcpHost.port}
            clusterName = 'elasticsearch_mpit'
            dispatcherType = '$dispatcherType'
        }
    """.stripIndent()
        buildFile << build
    }

    private getBuildId(String output) {
        def m = output =~ /Build id is (.*)/
        def buildId = m[0][1] as String
        return buildId
    }
}