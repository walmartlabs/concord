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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class RemoteOperationsTest {

    @Test
    void runsInTheCallingThread() throws Exception {
        var owner = Thread.currentThread();
        assertSame(owner, RemoteOperations.call(Duration.ofSeconds(1), Thread::currentThread));
    }

    @Test
    void disarmsTimerOnSuccessAndOnFailure() throws Exception {
        assertEquals("done", RemoteOperations.call(Duration.ofMillis(50), () -> "done"));
        var error = new IllegalStateException("upload failed");
        assertSame(error, assertThrows(IllegalStateException.class,
                () -> RemoteOperations.call(Duration.ofMillis(50), () -> { throw error; })));
        Thread.sleep(150);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void expiryDoesNotInterruptTheNextPhase() throws Exception {
        for (var i = 0; i < 30; i++) {
            assertThrows(TimeoutException.class, () -> RemoteOperations.call(Duration.ofMillis(2), () -> {
                new CountDownLatch(1).await();
                return null;
            }));
            assertFalse(Thread.currentThread().isInterrupted());
            assertEquals("state saved", RemoteOperations.call(Duration.ofSeconds(1), () -> {
                Thread.sleep(2);
                return "state saved";
            }));
        }
    }

    @Test
    void consumedHttpInterruptDoesNotStartAnotherRetry() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var task = new java.util.concurrent.FutureTask<Void>(() -> {
            assertThrows(com.walmartlabs.concord.client2.ApiException.class, () -> RemoteOperations.withRetry(() -> {
                calls.incrementAndGet();
                // File cleanup may clear the flag; the HTTP exception still identifies cancellation.
                Thread.interrupted();
                throw new com.walmartlabs.concord.client2.ApiException(new java.io.IOException(new InterruptedException()));
            }));
            assertTrue(Thread.currentThread().isInterrupted());
            return null;
        });
        var owner = Thread.ofVirtual().start(task);
        assertTrue(owner.join(Duration.ofSeconds(1)));
        task.get();
        assertEquals(1, calls.get());
    }

    @Test
    void timeoutIsAnErrorEvenIfActionSwallowsTheInterruptAndReturnsSuccess() {
        assertThrows(TimeoutException.class, () -> RemoteOperations.call(Duration.ofMillis(50), () -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
                // Model an I/O library consuming cancellation and returning normally.
            }
            return "success";
        }));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void doesNotReleaseResourcesBeforeCancelledActionActuallyExits() throws Exception {
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CompletableFuture<Throwable>();
        Thread.ofVirtual().start(() -> {
            try {
                RemoteOperations.call(Duration.ofMillis(50), () -> {
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        release.await();
                    }
                    return null;
                });
                finished.complete(null);
            } catch (Exception e) {
                finished.complete(e);
            }
        });
        try {
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertFalse(finished.isDone());
        } finally {
            release.countDown();
        }
        assertInstanceOf(TimeoutException.class, finished.get(2, TimeUnit.SECONDS));
    }

    @Test
    void cancellationReachesTheActionAndRemainsVisibleToTheOwner() throws Exception {
        var entered = new CountDownLatch(1);
        var result = new CompletableFuture<Boolean>();
        var owner = Thread.ofVirtual().start(() -> {
            try {
                RemoteOperations.call(Duration.ofSeconds(10), () -> {
                    entered.countDown();
                    new CountDownLatch(1).await();
                    return null;
                });
                result.complete(false);
            } catch (InterruptedException e) {
                result.complete(Thread.currentThread().isInterrupted());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        owner.interrupt();
        assertTrue(result.get(2, TimeUnit.SECONDS));
    }


}
