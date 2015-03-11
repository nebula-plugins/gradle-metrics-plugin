package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.Test;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.slf4j.Logger;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Collector for Gradle test suite metrics, implementing the {@link TestListener} interface.
 *
 * @author Danny Thomas
 */
public class GradleTestSuiteCollector implements TestListener {
    private static final Logger logger = MetricsLoggerFactory.getLogger(GradleTestSuiteCollector.class);
    private final MetricsDispatcher dispatcher;

    public GradleTestSuiteCollector(MetricsDispatcher dispatcher) {
        this.dispatcher = checkNotNull(dispatcher);
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
        checkNotNull(suite);
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        checkNotNull(suite);
        checkNotNull(result);
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
        checkNotNull(testDescriptor);
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult testResult) {
        checkNotNull(testDescriptor);
        checkNotNull(testResult);
        Result result = getTestResult(testResult);
        String suiteName = getSuiteName(testDescriptor);
        long startTime = testResult.getStartTime();
        long elapsed = testResult.getEndTime() - startTime;
        Test test = Test.create(testDescriptor.getName(), testDescriptor.getClassName(), suiteName, result, startTime, elapsed);
        dispatcher.test(test);
    }

    @VisibleForTesting
    String getSuiteName(TestDescriptor testDescriptor) {
        TestDescriptor rootDescriptor = testDescriptor;
        while (rootDescriptor.getParent() != null) {
            rootDescriptor = rootDescriptor.getParent();
        }
        // FIXME this appears to be always returning 'tests' regardless of whether we're dealing with unit tests or integration tests...
        // Use the sourceset as the suite name, which happens to be the toString representation of the description
        // This feels on the fragile side, but the alternative is splitting out the representation from getName(), which feels equally fragile
        return String.valueOf(rootDescriptor);
    }

    @VisibleForTesting
    Result getTestResult(TestResult testResult) {
        TestResult.ResultType testResultType = testResult.getResultType();
        List<Throwable> exceptions = testResult.getExceptions();
        Result result;
        switch (testResultType) {
            case SUCCESS:
                result = Result.success();
                break;
            case SKIPPED:
                result = Result.skipped();
                break;
            case FAILURE:
                //noinspection ConstantConditions
                result = Result.failure(exceptions);
                break;
            default:
                logger.warn("Test result carried unknown result type '{}'. Assuming success", testResultType);
                result = Result.success();
        }
        return result;
    }
}
