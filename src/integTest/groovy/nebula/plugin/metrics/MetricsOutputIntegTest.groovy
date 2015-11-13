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

    def 'Gradle metrics allows log4j output to be written to the console'() {
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
               testCompile 'log4j:log4j:latest.release'
               testCompile 'junit:junit:4.11'
            }

            test.testLogging.showStandardStreams = true
        """

        createFile('src/test/java/Test.java') << """\
            import org.apache.log4j.Logger;

            public class Test {
                Logger log = Logger.getLogger("test");

                @org.junit.Test
                public void iAmHeard() {
                    log.info("I want to be heard");
                }
            }
        """.stripIndent()

        createFile('src/test/resources/log4j.properties') << """\
            # Root logger option
            log4j.rootLogger=INFO, stdout

            # Direct log messages to stdout
            log4j.appender.stdout=org.apache.log4j.ConsoleAppender
            log4j.appender.stdout.Target=System.out
            log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
            log4j.appender.stdout.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} %-5p (%c{1}.java:%L) - %m%n
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('test')

        then:
        result.standardOutput.contains('I want to be heard')
    }
}
