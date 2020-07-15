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

import com.walmartlabs.concord.runtime.v2.model.AbstractStep;
import com.walmartlabs.concord.runtime.v2.parser.StepOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.lidalia.sysoutslf4j.context.LogLevel;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.*;

import static ch.qos.logback.classic.ClassicConstants.FINALIZE_SESSION_MARKER;

public class SegmentedLogger {

    private static final Logger log = LoggerFactory.getLogger(SegmentedLogger.class);

    private static volatile boolean ENABLED = false;

    public static void enable() {
        ENABLED = true;
    }

    public static void withLogSegment(String name, String segmentId, boolean redirectSystemOutAndErr, Runnable runnable) {
        if (!ENABLED) {
            try {
                runnable.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }

        ThreadGroup threadGroup = new SegmentThreadGroup(name, segmentId);
        executeInThreadGroup(threadGroup, "thread-" + name, () -> {
            // make sure the redirection is enabled in the current thread
            if (redirectSystemOutAndErr && !SysOutOverSLF4J.systemOutputsAreSLF4JPrintStreams()) {
                SysOutOverSLF4J.sendSystemOutAndErrToSLF4J(LogLevel.INFO, LogLevel.WARN);
            }

            try {
                runnable.run();
            } finally {
                log.info(FINALIZE_SESSION_MARKER, "<<finalize>>");
            }
        });
    }

    public static String getSegmentName(AbstractStep<?> step) {
        StepOptions opts = step.getOptions();
        if (opts == null) {
            return null;
        }

        Map<String, Serializable> meta = opts.meta();
        if (meta == null) {
            return null;
        }

        // TODO constants
        return (String) meta.get("segmentName");
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

    private static final class ThreadGroupAwareThreadFactory implements ThreadFactory {

        private final ThreadGroup group;
        private final String threadName;

        private ThreadGroupAwareThreadFactory(ThreadGroup group, String threadName) {
            this.group = group;
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, threadName);
        }
    }
}
