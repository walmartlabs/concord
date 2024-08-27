package com.walmartlabs.concord.runtime.common.logger;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ProcessLogStreamer {

    private final Path srcFile;
    private final long maxDelay;
    private final Consumer<Chunk> sink;

    public ProcessLogStreamer(Path srcFile, long maxDelay, Consumer<Chunk> sink) {
        this.srcFile = srcFile;
        this.maxDelay = maxDelay;
        this.sink = sink;
    }

    public void run(Supplier<Boolean> stopCondition) throws Exception {
        long total = 0;

        byte[] ab = new byte[8192];

        try (InputStream in = Files.newInputStream(srcFile, StandardOpenOption.READ)) {
            while (true) {
                int read = in.read(ab, 0, ab.length);
                if (read > 0) {
                    sink.accept(new Chunk(ab, read));
                    total += read;
                }

                if (read < ab.length) {
                    if (stopCondition.get() && total >= Files.size(srcFile)) {
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

    public static class Chunk {

        private final byte[] ab;
        private final int len;

        protected Chunk(byte[] ab, int len) { // NOSONAR
            this.ab = ab;
            this.len = len;
        }

        public byte[] bytes() {
            return ab;
        }

        public int len() {
            return len;
        }
    }
}
