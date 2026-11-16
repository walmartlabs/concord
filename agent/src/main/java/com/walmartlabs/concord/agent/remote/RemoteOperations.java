package com.walmartlabs.concord.agent.remote;

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

import com.walmartlabs.concord.agent.AgentConstants;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ClientUtils;

import java.nio.channels.ClosedByInterruptException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Agent-only deadlines and cancellation handling for the existing HTTP client. */
public final class RemoteOperations {

    /** Runs in the caller's thread; a timer only interrupts it when the budget expires. */
    public static <T> T call(Duration timeout, Callable<T> action) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Operation cancelled");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new TimeoutException("Operation deadline exceeded");
        }
        var owner = Thread.currentThread();
        var expired = new AtomicBoolean();
        var timer = Thread.ofVirtual().name("agent-operation-deadline").start(() -> {
            try {
                Thread.sleep(timeout);
                synchronized (expired) {
                    if (!Thread.currentThread().isInterrupted()) {
                        expired.set(true);
                        owner.interrupt();
                    }
                }
            } catch (InterruptedException ignored) {
                // The operation finished before its deadline.
            }
        });
        try {
            return action.call();
        } catch (InterruptedException e) {
            owner.interrupt();
            throw e;
        } finally {
            // Serialize disarming with expiry so a late timer cannot interrupt the next phase.
            synchronized (expired) {
                timer.interrupt();
                if (expired.get()) {
                    // Clear the deadline-induced interrupt before reporting the timeout.
                    Thread.interrupted();
                    throw new TimeoutException("Operation deadline exceeded (" + timeout + ")");
                }
            }
        }
    }

    /** Exit the existing retry loop before its logging/backoff can consume cancellation. */
    public static <T> T withRetry(Callable<T> action) throws ApiException {
        var cancelled = new AtomicReference<Exception>();
        var result = ClientUtils.<T>withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
            try {
                return action.call();
            } catch (Exception e) {
                for (var cause = (Throwable) e; cause != null; cause = cause.getCause()) {
                    if (cause instanceof InterruptedException || cause instanceof ClosedByInterruptException) {
                        cancelled.set(e);
                        return null;
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    cancelled.set(e);
                    return null;
                }
                throw e;
            }
        });
        if (cancelled.get() != null) {
            Thread.currentThread().interrupt();
            throw cancelled.get() instanceof ApiException error ? error : new ApiException(cancelled.get());
        }
        return result;
    }

    /** Wait for actual termination before deleting files used by the thread. */
    public static void join(Thread thread) {
        var interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
                thread.interrupt();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private RemoteOperations() {
    }
}
