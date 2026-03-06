package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ProcessOutputPump {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final long flushIntervalMillis;
    private final int bufferSize;

    public ProcessOutputPump(long flushIntervalMillis) {
        this(flushIntervalMillis, DEFAULT_BUFFER_SIZE);
    }

    ProcessOutputPump(long flushIntervalMillis, int bufferSize) {
        this.flushIntervalMillis = flushIntervalMillis;
        this.bufferSize = bufferSize;
    }

    public void pump(InputStream input, ProcessOutputSink output) throws IOException {
        var pendingFlush = new AtomicBoolean(false);
        var failureRef = new AtomicReference<IOException>();
        var outputLock = new Object();
        var flushExecutor = flushIntervalMillis > 0
                ? newFlushExecutor(output, pendingFlush, failureRef, outputLock)
                : null;

        IOException failure = null;
        try (var in = input) {
            var buffer = new byte[bufferSize];
            while (true) {
                checkFailure(failureRef);

                var read = in.read(buffer, 0, buffer.length);
                if (read < 0) {
                    break;
                }

                if (read == 0) {
                    continue;
                }

                synchronized (outputLock) {
                    checkFailure(failureRef);
                    output.write(buffer, 0, read);
                    pendingFlush.set(true);

                    if (flushIntervalMillis <= 0) {
                        output.flush();
                        pendingFlush.set(false);
                    }
                }
            }
        } catch (IOException e) {
            failure = e;
        } finally {
            if (flushExecutor != null) {
                flushExecutor.shutdownNow();
                try {
                    flushExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            try {
                flushPending(output, pendingFlush, outputLock);
                checkFailure(failureRef);
            } catch (IOException e) {
                failure = merge(failure, e);
            }

            try {
                output.close();
            } catch (IOException e) {
                failure = merge(failure, e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private ScheduledExecutorService newFlushExecutor(ProcessOutputSink output,
                                                      AtomicBoolean pendingFlush,
                                                      AtomicReference<IOException> failureRef,
                                                      Object outputLock) {

        var executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "process-output-pump-flusher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            try {
                flushPending(output, pendingFlush, outputLock);
            } catch (IOException e) {
                failureRef.compareAndSet(null, e);
            }
        }, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS);
        return executor;
    }

    private static void flushPending(ProcessOutputSink output, AtomicBoolean pendingFlush, Object outputLock) throws IOException {
        if (!pendingFlush.get()) {
            return;
        }

        synchronized (outputLock) {
            if (!pendingFlush.get()) {
                return;
            }

            output.flush();
            pendingFlush.set(false);
        }
    }

    private static void checkFailure(AtomicReference<IOException> failureRef) throws IOException {
        var failure = failureRef.get();
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException merge(IOException current, IOException next) {
        if (current == null) {
            return next;
        }

        current.addSuppressed(next);
        return current;
    }
}
