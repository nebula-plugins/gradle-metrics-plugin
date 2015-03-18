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


package nebula.plugin.metrics.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Lists
import spock.lang.Specification

/**
 * Tests for {@link KeyValue}.
 */
class KeyValueTest extends Specification {
    def 'mapper writes value with expected form'() {
        def keyValue = new KeyValue('mykey', 'myvalue')
        def mapper = new ObjectMapper()

        expect:
        mapper.writeValueAsString(keyValue) == '{"key":"mykey","value":"myvalue"}'
    }

    def 'mapper writes list of values with expected form'() {
        def keyValue = new KeyValue('mykey', 'myvalue')
        def mapper = new ObjectMapper()
        def list = Lists.newArrayList(keyValue, keyValue)

        expect:
        mapper.writeValueAsString(list) == '[{"key":"mykey","value":"myvalue"},{"key":"mykey","value":"myvalue"}]'
    }
}
