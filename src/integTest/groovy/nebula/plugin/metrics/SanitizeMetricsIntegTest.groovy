package nebula.plugin.metrics

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import nebula.test.IntegrationSpec
import org.junit.Rule

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.containing
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor

class SanitizeMetricsIntegTest extends IntegrationSpec {

    @Rule
    WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicPort())

    def 'properties are sanitized via custom regex'() {
        setup:
        def regex = "(?i).*\\\\_(ID)\\\$"
        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                sanitizedPropertiesRegex = "$regex"
                dispatcherType = 'SPLUNK'
                splunkInputType = 'FORWARDER'
                splunkUri = 'http://localhost:${wireMockRule.port()}/myserver'
                headers['Authorization'] = 'Basic YWRtaW46Y2hhbmdlbWU='
            }
        """.stripIndent()


        //build is finished
        stubFor(post('/myserver')
                .withHeader("Authorization", equalTo("Basic YWRtaW46Y2hhbmdlbWU="))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("project\":{\"name\":\"${moduleName}\",\"version\":\"unspecified\"}"))
                .withRequestBody(containing("{\"MY_ID\":\"SANITIZED\"}"))
                .willReturn(
                aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        expect:
        runTasksSuccessfully('-DMY_ID=myvalue1', '-Dsomething=value5', 'projects')
    }

    def 'properties are sanitized via default regex'() {
        setup:
        buildFile << """
            ${applyPlugin(MetricsPlugin)}

            metrics {
                dispatcherType = 'SPLUNK'
                splunkInputType = 'FORWARDER'
                splunkUri = 'http://localhost:${wireMockRule.port()}/myserver'
                headers['Authorization'] = 'Basic YWRtaW46Y2hhbmdlbWU='
            }
        """.stripIndent()


        //build is finished
        stubFor(post('/myserver')
                .withHeader("Authorization", equalTo("Basic YWRtaW46Y2hhbmdlbWU="))
                .withRequestBody(containing("buildId"))
                .withRequestBody(containing("startTime"))
                .withRequestBody(containing("finishedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("elapsedTime"))
                .withRequestBody(containing("project\":{\"name\":\"${moduleName}\",\"version\":\"unspecified\"}"))
                .withRequestBody(containing("{\"MY_KEY\":\"SANITIZED\"}"))
                .withRequestBody(containing("{\"MY_PASSWORD\":\"SANITIZED\"}"))
                .withRequestBody(containing("{\"MY_SECRET\":\"SANITIZED\"}"))
                .withRequestBody(containing("{\"MY_TOKEN\":\"SANITIZED\"}"))
                .willReturn(
                aResponse().withStatus(200).withHeader('Content-Type', 'application/json')))

        expect:
        runTasksSuccessfully('-DMY_KEY=myvalue1', '-DMY_PASSWORD=myvalue2', '-DMY_SECRET=myvalue3', '-DMY_TOKEN=myvalue4', '-Dsomething=value5', 'projects')
    }

}