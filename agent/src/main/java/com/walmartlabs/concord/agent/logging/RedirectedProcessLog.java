package com.walmartlabs.concord.agent.logging;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Log that uses a local file as a buffer before sending the data into the specified {@link LogAppender}.
 * Typically, {@link #run(Supplier)} method should be executed in a separate thread.
 */
public class RedirectedProcessLog implements ProcessLog {

    private final UUID instanceId;
    private final LogAppender appender;
    private final long logSteamMaxDelay;

    private final LocalProcessLog localLog;

    public RedirectedProcessLog(Path baseDir, UUID instanceId, LogAppender appender, long logSteamMaxDelay) throws IOException {
        this.instanceId = instanceId;
        this.appender = appender;
        this.logSteamMaxDelay = logSteamMaxDelay;
        this.localLog = new LocalProcessLog(baseDir, instanceId);
    }

    public void run(Supplier<Boolean> stopCondition) throws Exception {
        Consumer<Chunk> sink = chunk -> {
            byte[] ab = new byte[chunk.len];
            System.arraycopy(chunk.ab, 0, ab, 0, chunk.len);
            appender.appendLog(instanceId, ab);
        };

        streamLog(localLog.logFile(), stopCondition, logSteamMaxDelay, sink);
    }

    @Override
    public void delete() {
        this.localLog.delete();
    }

    @Override
    public void log(InputStream src) throws IOException {
        this.localLog.log(src);
    }

    @Override
    public void info(String log, Object... args) {
        this.localLog.info(log, args);
    }

    @Override
    public void warn(String log, Object... args) {
        this.localLog.warn(log, args);
    }

    @Override
    public void error(String log, Object... args) {
        this.localLog.error(log, args);
    }

    private static void streamLog(Path p, Supplier<Boolean> stopCondition, long maxDelay, Consumer<Chunk> sink) throws IOException {
        long total = 0;

        byte[] ab = new byte[8192];

        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
            while (true) {
                int read = in.read(ab, 0, ab.length);
                if (read > 0) {
                    sink.accept(new Chunk(ab, read));
                    total += read;
                }

                if (read < ab.length) {
                    if (stopCondition.get() && total >= Files.size(p)) {
                        // the log and the job are finished
                        break;
                    }

                    // job is still running, wait for more data
                    try {
                        Thread.sleep(maxDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private static final class Chunk {

        private final byte[] ab;
        private final int len;

        private Chunk(byte[] ab, int len) { // NOSONAR
            this.ab = ab;
            this.len = len;
        }
    }
}
