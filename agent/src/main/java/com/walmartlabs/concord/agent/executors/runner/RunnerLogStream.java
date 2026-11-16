package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.agent.logging.LogStreamingException;
import com.walmartlabs.concord.agent.remote.RemoteOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/** Streams during execution, then waits a bounded time for the remaining local log. */
final class RunnerLogStream implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RunnerLogStream.class);

    private final UUID instanceId;
    private final RunnerLog processLog;
    private final Duration drainTimeout;
    private final Thread sender;
    private volatile boolean inputComplete;
    private volatile String stopReason;
    private boolean reported;

    RunnerLogStream(UUID instanceId, RunnerLog processLog, Duration drainTimeout) {
        this.instanceId = instanceId;
        this.processLog = processLog;
        this.drainTimeout = drainTimeout;
        sender = Thread.ofVirtual().name("log-stream-" + instanceId).start(() -> {
            try {
                processLog.run(() -> inputComplete);
            } catch (Exception e) {
                var expected = stopReason != null || e instanceof LogStreamingException || e instanceof InterruptedException;
                recordStop(e.getMessage());
                if (!expected) {
                    log.warn("log streaming [{}] -> sender failed unexpectedly; runner execution is unaffected", instanceId, e);
                }
            }
        });
    }

    /** Returns a reason to include in the process log when delivery was incomplete. */
    String finish() throws InterruptedException {
        inputComplete = true;
        try {
            if (!sender.join(drainTimeout)) {
                recordStop("Log drain deadline exceeded (" + drainTimeout + ")");
            }
        } finally {
            close();
        }
        return stopReason;
    }

    private synchronized void recordStop(String reason) {
        if (stopReason == null) {
            stopReason = reason != null ? reason : "Log streaming failed";
        }
    }

    @Override
    public void close() {
        if (sender.isAlive()) {
            recordStop("Log streaming closed");
            sender.interrupt();
            RemoteOperations.join(sender);
        }
        if (!reported && stopReason != null) {
            reported = true;
            log.warn("log streaming [{}] -> stopped: {}; unprocessed local bytes: {} " +
                            "(includes any partially processed chunk, excludes earlier skipped segments)",
                    instanceId, stopReason, processLog.remainingBytes());
        }
    }
}
