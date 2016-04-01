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

package nebula.plugin.metrics.dispatcher

import com.google.common.base.Predicate;
import com.google.common.testing.AbstractPackageSanityTests

import javax.annotation.Nullable;

/**
 * Sanity checks for {@link nebula.plugin.metrics.collector}.
 */
public class PackageSanityTest extends AbstractPackageSanityTests {
    PackageSanityTest() {
        ignoreClasses(new Predicate<Class<?>>() {
            @Override
            boolean apply(@Nullable Class<?> input) {
                return input == UninitializedMetricsDispatcher || input == NoopMetricsDispatcher
            }
        })
    }
}
