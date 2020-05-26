package nebula.plugin.metrics

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.collect.FluentIterable
import nebula.test.functional.GradleRunner
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.util.TextUtil

class DependenciesBuilderWithClassesUnderTest {
    static String buildDependencies() {
        ClassLoader classLoader = DependenciesBuilderWithClassesUnderTest.class.getClassLoader()
        def classpathFilter = GradleRunner.CLASSPATH_DEFAULT
        getClasspathAsFiles(classLoader, classpathFilter).collect {
            String.format("      classpath files('%s')\n", TextUtil.escapeString(it.getAbsolutePath()))
        }.join('\n')
    }

    private static List<File> getClasspathAsFiles(ClassLoader classLoader, Predicate<URL> classpathFilter) {
        List<URL> classpathUrls = getClasspathUrls(classLoader)
        return FluentIterable.from(classpathUrls).filter(classpathFilter).transform(new Function<URL, File>() {
            @Override
            File apply(URL url) {
                return new File(url.toURI())
            }
        }).toList()
    }

    private static List<URL> getClasspathUrls(ClassLoader classLoader) {
        Object cp = ClasspathUtil.getClasspath(classLoader)
        if (cp instanceof List<URL>) {
            return (List<URL>) cp
        }
        if (cp instanceof ClassPath) { // introduced by gradle/gradle@0ab8bc2
            return ((ClassPath) cp).asURLs
        }
        throw new IllegalStateException("Unable to extract classpath urls from type ${cp.class.canonicalName}")
    }

}
