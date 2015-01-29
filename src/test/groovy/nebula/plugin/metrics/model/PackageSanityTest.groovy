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
