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

package nebula.plugin.metrics.collector;

import nebula.plugin.metrics.MetricsLoggerFactory;
import nebula.plugin.metrics.dispatcher.MetricsDispatcher;
import nebula.plugin.metrics.model.Result;
import nebula.plugin.metrics.model.Test;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.joda.time.DateTime;
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
        Test test = new Test(testDescriptor.getName(), testDescriptor.getClassName(), suiteName, result, new DateTime(startTime), elapsed);
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
