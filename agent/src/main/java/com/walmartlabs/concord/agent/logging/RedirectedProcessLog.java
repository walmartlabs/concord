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

import com.walmartlabs.concord.runtime.common.logger.ProcessLog;
import com.walmartlabs.concord.runtime.common.logger.ProcessLogStreamer;
import com.walmartlabs.concord.runtime.common.logger.ProcessLogStreamer.Chunk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Log that uses a local file as a buffer before sending the data into the specified {@link com.walmartlabs.concord.runtime.common.logger.LogAppender}.
 * Typically, {@link #run(Supplier)} method should be executed in a separate thread.
 */
public class RedirectedProcessLog implements ProcessLog {

    private final LocalProcessLog localLog;
    private final ProcessLogStreamer streamer;

    public RedirectedProcessLog(Path baseDir, long logSteamMaxDelay, Consumer<Chunk> consumer) throws IOException {
        this.localLog = new LocalProcessLog(baseDir);
        this.streamer = new ProcessLogStreamer(localLog.logFile(), logSteamMaxDelay, consumer);
    }

    public void run(Supplier<Boolean> stopCondition) throws Exception {
        streamer.run(stopCondition);
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
}
