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

package nebula.plugin.metrics.model;

import com.google.common.base.Throwables;
import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Result.
 */
@Value
public class Result {
    public static Result unknown() {
        return create(ResultStatus.UNKNOWN);
    }

    public static Result success() {
        return create(ResultStatus.SUCCESS);
    }

    public static Result failure(Throwable throwable) {
        return failure(Arrays.asList(throwable));
    }

    public static Result failure(Iterable<? extends Throwable> failures) {
        checkNotNull(failures);
        List<String> stringFailures = new ArrayList<>();
        for (Throwable throwable : failures) {
            stringFailures.add(Throwables.getStackTraceAsString(throwable));
        }
        return new Result(ResultStatus.FAILURE, stringFailures);
    }

    public static Result skipped() {
        return create(ResultStatus.SKIPPED);
    }

    private static Result create(ResultStatus status) {
        return new Result(status, null);
    }

    @NonNull
    private ResultStatus status;

    private List<String> failures;

    public enum ResultStatus {
        UNKNOWN,
        SUCCESS,
        FAILURE,
        SKIPPED
    }
}
