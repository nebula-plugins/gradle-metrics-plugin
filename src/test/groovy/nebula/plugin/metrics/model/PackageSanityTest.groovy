/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.base.Predicate
import com.google.common.testing.AbstractPackageSanityTests

import javax.annotation.Nullable

/**
 * Sanity checks for {@link nebula.plugin.metrics.model}.
 */
public class PackageSanityTest extends AbstractPackageSanityTests {
    def PackageSanityTest() {
        ignoreClasses(new Predicate<Class<?>>() {
            @Override
            boolean apply(@Nullable Class<?> input) {
                // Ignore auto value classes because their equals methods fail null tests
                input.getSimpleName().startsWith("AutoValue")
            }
        })
        def scm = SCM.detect()
        def ci = CI.detect()
        setDefault(SCM, scm)
        setDefault(CI, ci)
        setDefault(Result, Result.success());
        setDefault(Project, Project.create("project", "1.0"))
    }
}
