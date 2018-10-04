/*
 *  Copyright 2015-2016 Netflix, Inc.
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

package nebula.plugin.metrics.model

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for {@link Info}.
 */
class InfoTest extends Specification {
    def 'environment is json serialized in expected form'() {
        def map = new LinkedHashMap<String, String>()
        map.put("mykey1", "myvalue1")
        map.put("mykey2", "myvalue2")
        def tool = Mock(Tool)
        def info = Info.create(tool, tool, tool, map, Collections.emptyMap())
        def mapper = new ObjectMapper()

        expect:
        mapper.writeValueAsString(info) == '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[],"javaVersion":"unknown","detailedJavaVersion":"unknown"}'
    }

    def 'properties are sanisited'() {
        def map = new LinkedHashMap<String, String>()
        map.put("mykey1", "myvalue1")
        map.put("mykey2", "myvalue2")
        def tool = Mock(Tool)
        def info = Info.create(tool, tool, tool, Collections.emptyMap(), map)

        expect:
        def sanitizedInfo = Info.sanitize(info, Arrays.asList("mykey1"))
        sanitizedInfo.systemProperties.find { it.key == "mykey1" }.value == "SANITIZED"
    }

    @Unroll
    def 'enhances Java version with details from runtime name and vendor - #description'() {
        def env = new LinkedHashMap<String, String>()
        env.put("mykey1", "myvalue1")
        env.put("mykey2", "myvalue2")
        def systemProperties = new LinkedHashMap<String, String>()
        systemProperties.put("java.runtime.name", runtimeName)
        systemProperties.put("java.version", version)
        systemProperties.put("java.vm.vendor", vendor)
        def tool = Mock(Tool)
        def info = Info.create(tool, tool, tool, env, systemProperties)
        def mapper = new ObjectMapper()

        expect:
        mapper.writeValueAsString(info) == expectedResult

        where:
        description                       | version     | runtimeName                         | vendor         | expectedResult
        "is Oracle"                       | "1.8.0_181" | "Oracle"                            | ""             | '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[{"key":"java.runtime.name","value":"Oracle"},{"key":"java.version","value":"1.8.0_181"},{"key":"java.vm.vendor","value":""}],"javaVersion":"1.8.0_181","detailedJavaVersion":"Oracle 1.8.0_181"}'
        "is Oracle"                       | "1.8.0_181" | "Java(TM) SE Runtime Environment"   | ""             | '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[{"key":"java.runtime.name","value":"Java(TM) SE Runtime Environment"},{"key":"java.version","value":"1.8.0_181"},{"key":"java.vm.vendor","value":""}],"javaVersion":"1.8.0_181","detailedJavaVersion":"Oracle 1.8.0_181"}'
        "is Oracle"                       | "1.8.0_181" | "Java HotSpot(TM) 64-Bit Server VM" | ""             | '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[{"key":"java.runtime.name","value":"Java HotSpot(TM) 64-Bit Server VM"},{"key":"java.version","value":"1.8.0_181"},{"key":"java.vm.vendor","value":""}],"javaVersion":"1.8.0_181","detailedJavaVersion":"Oracle 1.8.0_181"}'
        "is OpenJDK - zulu"               | "1.8.0_181" | "OpenJDK"                           | "Azul Systems" | '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[{"key":"java.runtime.name","value":"OpenJDK"},{"key":"java.version","value":"1.8.0_181"},{"key":"java.vm.vendor","value":"Azul Systems"}],"javaVersion":"1.8.0_181","detailedJavaVersion":"OpenJDK 1.8.0_181-zulu"}'
        "is OpenJDK"                      | "1.8.0_181" | "OpenJDK"                           | ""             | '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[{"key":"java.runtime.name","value":"OpenJDK"},{"key":"java.version","value":"1.8.0_181"},{"key":"java.vm.vendor","value":""}],"javaVersion":"1.8.0_181","detailedJavaVersion":"OpenJDK 1.8.0_181"}'
        "is case insensitive for openjdk" | "1.8.0_181" | "openjdk"                           | ""             | '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[{"key":"java.runtime.name","value":"openjdk"},{"key":"java.version","value":"1.8.0_181"},{"key":"java.vm.vendor","value":""}],"javaVersion":"1.8.0_181","detailedJavaVersion":"OpenJDK 1.8.0_181"}'
    }
}
