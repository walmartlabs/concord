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

import com.walmartlabs.concord.runtime.v2.runner.vm.TaskThreadGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static ch.qos.logback.classic.ClassicConstants.FINALIZE_SESSION_MARKER;

public class SegmentedLogger {

    private static final Logger log = LoggerFactory.getLogger(SegmentedLogger.class);

    private static boolean ENABLED = false;

    public static void enable() {
        ENABLED = true;
    }

    /**
     * Runs the specified {@link Callable} in a separate log segment.
     */
    public static <V> V withLogSegment(String name, String segmentId, Callable<V> callable) {
        if (!ENABLED) {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ThreadGroup threadGroup = new TaskThreadGroup(name, segmentId);
        return executeInThreadGroup(threadGroup, "thread-" + name, () -> {
            try {
                return callable.call();
            } finally {
                log.info(FINALIZE_SESSION_MARKER, "The End!");
            }
        });
    }

    /**
     * Executes the {@link Callable} in the specified {@link ThreadGroup}.
     * A bit expensive as it is creates a new thread.
     */
    private static <V> V executeInThreadGroup(ThreadGroup group, String threadName, Callable<V> callable) {
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadGroupAwareThreadFactory(group, threadName));
        Future<V> result = executor.submit(callable);
        try {
            return result.get();
        } catch (InterruptedException | ExecutionException e) {
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
