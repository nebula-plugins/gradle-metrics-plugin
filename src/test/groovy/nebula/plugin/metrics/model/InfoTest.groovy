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
        mapper.writeValueAsString(info) == '{"build":{"type":null},"scm":{"type":null},"ci":{"type":null},"environmentVariables":[{"key":"mykey1","value":"myvalue1"},{"key":"mykey2","value":"myvalue2"}],"systemProperties":[],"javaVersion":"unknown"}'
    }

    def 'properties are sanisited'() {
        def map = new LinkedHashMap<String, String>()
        map.put("mykey1", "myvalue1")
        map.put("mykey2", "myvalue2")
        def tool = Mock(Tool)
        def info = Info.create(tool, tool, tool, Collections.emptyMap(), map)

        expect:
        def sanitizedInfo = Info.sanitize(info, Arrays.asList("mykey1"))
        sanitizedInfo.systemProperties.find{ it.key == "mykey1" }.value == "SANITIZED"
    }
}
