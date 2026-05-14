package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import ch.qos.logback.classic.Level;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.AbstractStep;
import com.walmartlabs.concord.runtime.v2.parser.StepOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.lidalia.sysoutslf4j.context.LogLevel;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class SegmentedLogger implements RunnerLogger {

    private static final long SYSTEM_SEGMENT_ID = 0;
    public static final String SYSTEM_SEGMENT_NAME = "system";

    private static final Logger log = LoggerFactory.getLogger(SegmentedLogger.class);

    private final LoggingClient loggingClient;

    public SegmentedLogger(LoggingClient loggingClient) {
        this.loggingClient = loggingClient;
    }

    @Override
    public Long createSegment(String segmentName, UUID correlationId, Long parentSegmentId, Map<String, Object> meta) {
        if (SYSTEM_SEGMENT_NAME.equals(segmentName)) {
            return SYSTEM_SEGMENT_ID;
        }
        return loggingClient.createSegment(correlationId, segmentName, parentSegmentId, meta);
    }

    @Override
    public void setSegmentStatus(long segmentId, LogSegmentStatus segmentStatus) {
        // now the system segment never closes...
        if (SYSTEM_SEGMENT_ID == segmentId) {
            return;
        }

        log.info(new SegmentStatusMarker(segmentId, segmentStatus), segmentStatus.name());
    }

    @Override
    public void withContext(LogContext context, Runnable runnable) {
        ThreadGroup threadGroup = new LogContextThreadGroup(context);
        executeInThreadGroup(threadGroup, "thread-" + context.segmentName(), () -> {
            // make sure the redirection is enabled in the current thread
            if (context.redirectSystemOutAndErr() && !SysOutOverSLF4J.systemOutputsAreSLF4JPrintStreams()) {
                SysOutOverSLF4J.sendSystemOutAndErrToSLF4J(LogLevel.INFO, LogLevel.ERROR);
            }

            runnable.run();
        });
    }

    public static String getSegmentName(AbstractStep<?> step) {
        Map<String, Serializable> meta = meta(step);
        return (String) meta.get(Constants.SEGMENT_NAME);
    }

    public static Level getLogLevel(AbstractStep<?> step) {
        Map<String, Serializable> meta = meta(step);
        String logLevel = (String) meta.get(Constants.LOG_LEVEL);
        return Level.toLevel(logLevel, Level.INFO);
    }

    private static Map<String, Serializable> meta(AbstractStep<?> step) {
        StepOptions opts = step.getOptions();
        if (opts == null) {
            return Collections.emptyMap();
        }

        return opts.meta();
    }

    /**
     * Executes the {@link Callable} in the specified {@link ThreadGroup}.
     * A bit expensive as it is creates a new thread.
     */
    private static void executeInThreadGroup(ThreadGroup group, String threadName, Runnable runnable) {
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadGroupAwareThreadFactory(group, threadName));
        Future<?> result = executor.submit(runnable);
        try {
            result.get();
        } catch (InterruptedException e) { // NOSONAR
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else {
                    throw new RuntimeException(cause);
                }
            }

            throw new RuntimeException(e);
        }
    }

    private record ThreadGroupAwareThreadFactory(ThreadGroup group, String threadName) implements ThreadFactory {

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, threadName);
        }
    }
}
