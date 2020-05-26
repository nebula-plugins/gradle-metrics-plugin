package nebula.plugin.metrics

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import nebula.test.IntegrationSpec
import org.junit.Rule

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.containing
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor

class RestMetricsInitScriptIntegrationTest extends IntegrationSpec {

    @Rule
    WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicPort())

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
    }

    def 'plugin can be applied at init script level'() {
        setup:
        buildFile << """
metrics {
    restUri = 'http://localhost:${wireMockRule.port()}/myserver'
    dispatcherType = 'REST'
}
"""

        //First post
        stubFor(post('/myserver')
                .withRequestBody(containing("\"eventName\":\"build_metrics\""))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .willReturn(
                        aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        //build is finished
        stubFor(post('/myserver')
                .withRequestBody(containing("\"eventName\":\"build_metrics\""))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("project\":{\"name\":\"${moduleName}\",\"version\":\"unspecified\"}"))
                .willReturn(
                        aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        when:
        def result = runTasksSuccessfully('build')

        then:
        result.standardOutput.contains("Metrics have been posted to http://localhost:${wireMockRule.port()}/myserver")
    }
}
