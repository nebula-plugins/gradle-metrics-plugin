package nebula.plugin.metrics
import nebula.test.IntegrationSpec

class MetricsOutputIntegTest extends IntegrationSpec {
    def 'Gradle metrics allows test standard output to be written to the console'() {
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
               testCompile 'junit:junit:4.11'
            }

            test.testLogging.showStandardStreams = true
        """

        createFile('src/test/java/Test.java') << """\
            public class Test {
                @org.junit.Test
                public void iAmHeard() {
                    System.out.println("I want to be heard");
                }
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('test')

        then:
        result.standardOutput.contains('I want to be heard')
    }
}
