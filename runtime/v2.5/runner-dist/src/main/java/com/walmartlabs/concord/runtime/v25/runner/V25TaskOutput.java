package com.walmartlabs.concord.runtime.v25.runner;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Routes direct task output through the redacting Logback layout without capturing runner output.
 *
 * <p>Capture is thread-local, so output written by threads spawned from a task is not redirected or redacted.
 */
final class V25TaskOutput {

    private static final OutputStream PROCESS_OUTPUT = System.out;
    private static final ThreadLocal<Integer> CAPTURING = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> EMITTING = ThreadLocal.withInitial(() -> false);
    private static boolean installed;

    static synchronized void install() {
        if (installed) {
            return;
        }
        System.setOut(new PrintStream(new RedirectingOutputStream(System.out, LoggerFactory.getLogger("v25.task.stdout"), false), true,
                StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new RedirectingOutputStream(System.err, LoggerFactory.getLogger("v25.task.stderr"), true), true,
                StandardCharsets.UTF_8));
        installed = true;
    }
    static OutputStream processOutput() {
        return PROCESS_OUTPUT;
    }


    static void enter() {
        CAPTURING.set(CAPTURING.get() + 1);
    }

    static void leave() {
        var depth = CAPTURING.get();
        if (depth == 0) {
            return;
        }
        System.out.flush();
        System.err.flush();
        if (depth == 1) {
            CAPTURING.remove();
        } else {
            CAPTURING.set(depth - 1);
        }
    }

    private static final class RedirectingOutputStream extends OutputStream {
        private final PrintStream delegate;
        private final Logger logger;
        private final boolean error;
        private final ThreadLocal<ByteArrayOutputStream> buffers = ThreadLocal.withInitial(ByteArrayOutputStream::new);

        private RedirectingOutputStream(PrintStream delegate, Logger logger, boolean error) {
            this.delegate = delegate;
            this.logger = logger;
            this.error = error;
        }

        @Override
        public void write(int value) {
            if (CAPTURING.get() == 0 || EMITTING.get()) {
                delegate.write(value);
                return;
            }
            var buffer = buffers.get();
            if (value == '\n') {
                emit(buffer);
            } else if (value != '\r') {
                buffer.write(value);
            }
        }

        @Override
        public void flush() {
            if (CAPTURING.get() > 0 && !EMITTING.get()) {
                emit(buffers.get());
            }
            delegate.flush();
        }

        private void emit(ByteArrayOutputStream buffer) {
            if (buffer.size() == 0) {
                return;
            }
            var line = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            EMITTING.set(true);
            try {
                if (error) {
                    logger.error("{}", line);
                } else {
                    logger.info("{}", line);
                }
            } finally {
                EMITTING.remove();
            }
        }
    }

    private V25TaskOutput() {
    }
}
