/*
 *  Copyright 2015-2019 Netflix, Inc.
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

package nebula.plugin.metrics.dispatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * An {@link AbstractQueuedExecutionThreadService} that allows actions of type <pre>E</pre> to be queued and executed in
 * the order received.
 *
 * @author Danny Thomas
 */
public abstract class AbstractQueuedExecutionThreadService<E> extends AbstractExecutionThreadService {
    private static final Set<State> QUEUE_AVAILABLE_STATES = Sets.newHashSet(State.STARTING, State.RUNNING, State.STOPPING);
    // We can't use the MetricsLoggerFactory here, or we'll get a feedback loop from the debug statements in the indexing critical paths
    private final Logger logger = LoggerFactory.getLogger(AbstractExecutionThreadService.class);
    private final BlockingQueue<E> queue;
    private final boolean failOnError;
    private final AtomicBoolean failed = new AtomicBoolean();
    private final boolean verboseErrorOuput;

    public AbstractQueuedExecutionThreadService(boolean failOnError, boolean verboseErrorOuput) {
        this(new LinkedBlockingQueue<E>(), failOnError, verboseErrorOuput);
    }

    @VisibleForTesting
    AbstractQueuedExecutionThreadService(BlockingQueue<E> queue, boolean failOnError, boolean verboseErrorOutput) {
        this.queue = checkNotNull(queue);
        this.failOnError = failOnError;
        this.verboseErrorOuput = verboseErrorOutput;
    }

    protected abstract void execute(E action) throws Exception;

    @Override
    protected final void run() throws Exception {
        while (isRunning() || !queue.isEmpty()) {
            E action = queue.poll(100, TimeUnit.MILLISECONDS);
            doExecute(action);
        }
        logger.debug("Service is not running and queue is empty, returning from run()");
    }

    private void doExecute(@Nullable E action) {
        try {
            if (action != null) {
                logger.debug("Executing {}", action);
                execute(action);
            }
        } catch (Exception e) {
            logger.debug("Error executing metrics action {}: {}", action, getRootCauseMessage(e));
            if (failOnError) {
                logger.debug("Shutting down {} due to previous failure", this);
                queue.clear();
                failed.set(true);
                if (verboseErrorOuput)
                    throw Throwables.propagate(e);
            }
        }
    }

    protected final boolean hasFailed() {
        return failed.get();
    }

    @Override
    protected final void shutDown() throws Exception {
        try {
            beforeShutDown(); // We want any problems with the before shutdown hook to prevent queue draining, so we handle that inside this try

            logger.debug("Shutting down queued execution service {}. Draining queue...", this);
            List<E> remaining = Lists.newArrayListWithCapacity(queue.size());
            queue.drainTo(remaining);
            for (E e : remaining) {
                execute(e);
            }
            checkState(queue.isEmpty(), "The queue should have been drained before shutdown");
        } catch (Exception e) {
            logger.error("An error occurred during shutdown (error message: )", getRootCauseMessage(e));
        }
        postShutDown();
    }

    protected void beforeShutDown() throws Exception {
    }

    protected void postShutDown() throws Exception {
    }

    protected final void queue(E action) {
        checkNotNull(action);
        if (!QUEUE_AVAILABLE_STATES.contains(state())) {
            logger.debug("Dispatcher is not running, dropping action {}", action);
        } else if (isAsync()) {
            logger.debug("Queueing {}", action);
            queue.add(action);
        } else {
            doExecute(action);
        }
    }

    protected final void executeSynchronously(E action) {
        checkNotNull(action);
        if (!QUEUE_AVAILABLE_STATES.contains(state())) {
            logger.debug("Dispatcher is not running, dropping action {}", action);
        } else {
            doExecute(action);
        }
    }


    /**
     * Allow service to run non-asynchronously to allow unit testing of concrete implementations, without needing to
     * deal with timing issues.
     *
     * @return true if the service is asynchronous
     */
    protected boolean isAsync() {
        return true;
    }
}
