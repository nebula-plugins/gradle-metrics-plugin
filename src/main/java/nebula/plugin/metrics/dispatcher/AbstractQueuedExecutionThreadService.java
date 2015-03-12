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

package nebula.plugin.metrics.dispatcher;

import nebula.plugin.metrics.MetricsLoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link AbstractQueuedExecutionThreadService} that allows actions of type <pre>E</pre> to be queued and executed in
 * the order received.
 *
 * @author Danny Thomas
 */
public abstract class AbstractQueuedExecutionThreadService<E> extends AbstractExecutionThreadService {
    private final Logger logger = MetricsLoggerFactory.getLogger(AbstractExecutionThreadService.class);
    private final BlockingQueue<E> queue;
    private final boolean shutdownOnFailure;

    public AbstractQueuedExecutionThreadService(boolean shutdownOnFailure) {
        this(new LinkedBlockingQueue<E>(), shutdownOnFailure);
    }

    @VisibleForTesting
    AbstractQueuedExecutionThreadService(BlockingQueue<E> queue, boolean shutdownOnFailure) {
        this.queue = checkNotNull(queue);
        this.shutdownOnFailure = shutdownOnFailure;
    }

    protected abstract void execute(E action) throws Exception;

    protected void postShutDown() throws Exception {
    }

    @Override
    protected final void run() throws Exception {
        while (isRunning() || !queue.isEmpty()) {
            E action = queue.poll(100, TimeUnit.MILLISECONDS);
            doExecute(action);
        }
    }

    private void doExecute(@Nullable E action) {
        try {
            if (action != null) {
                logger.debug("Executing {}", action);
                execute(action);
            }
        } catch (Exception e) {
            logger.error("Error executing action {}: {}", action, e.getMessage(), e);
            if (shutdownOnFailure) {
                logger.info("Shutting down {} due to previous failure", this);
                queue.clear();
                stopAsync().awaitTerminated();
            }
        }
    }

    @Override
    protected final void shutDown() throws Exception {
        logger.debug("Shutting down queued execution service {}", this);
        while (!queue.isEmpty()) {
            logger.debug("Waiting for queue to drain...");
            Thread.sleep(500);
        }
        checkState(queue.isEmpty(), "The queue should have been drained before shutdown");
        postShutDown();
    }

    protected final void queue(E action) {
        checkNotNull(action);
        if (!(state() == State.STARTING || state() == State.RUNNING)) {
            logger.debug("Dispatcher is not running, dropping action {}", action);
            return;
        } else if (isAsync()) {
            logger.debug("Queueing {}", action);
            queue.add(action);
        } else {
            doExecute(action);
        }
    }


    /**
     * Allow service to run non-asynchronously to allow unit testing of concrete implementations, without needing to
     * deal with timing issues.
     */
    protected boolean isAsync() {
        return true;
    }
}
