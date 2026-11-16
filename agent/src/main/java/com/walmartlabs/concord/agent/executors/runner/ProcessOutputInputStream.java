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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Reads a ProcessBuilder pipe without waiting for EOF from inherited writers.
 * Must be the only application reader of the process output. After observing
 * process exit, drains the bytes available at that point and then returns EOF.
 */
final class ProcessOutputInputStream extends InputStream {

    private static final long MAX_IDLE_WAIT_MILLIS = 50;

    private final Process process;
    private final InputStream input;
    private int remaining = -1;
    private long idleWaitMillis = 1;

    ProcessOutputInputStream(Process process) {
        this.process = process;
        this.input = process.getInputStream();
    }

    @Override
    public int read() throws IOException {
        var one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(one[0]);
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) {
            return 0;
        }
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Interrupted while reading runner output");
            }
            if (remaining < 0 && !process.isAlive()) {
                // A descendant may keep writing. Do not move this boundary as
                // more output arrives after the runner has exited.
                remaining = input.available();
            }
            if (remaining == 0) {
                return -1;
            }
            var available = remaining >= 0 ? remaining : input.available();
            if (available > 0) {
                var read = input.read(bytes, offset, Math.min(length, available));
                if (read < 0) {
                    remaining = 0;
                    return -1;
                }
                if (remaining >= 0) {
                    remaining -= read;
                }
                idleWaitMillis = 1;
                return read;
            }
            try {
                // Back off for quiet processes; waitFor wakes immediately on
                // exit. Never issue a potentially blocking read on an empty pipe.
                process.waitFor(idleWaitMillis, TimeUnit.MILLISECONDS);
                idleWaitMillis = Math.min(MAX_IDLE_WAIT_MILLIS, idleWaitMillis * 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                var interrupted = new InterruptedIOException("Interrupted while reading runner output");
                interrupted.initCause(e);
                throw interrupted;
            }
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
