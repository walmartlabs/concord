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

import com.walmartlabs.concord.client.LogSegmentUpdateRequest;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

public class SegmentedProcessLog extends RedirectedProcessLog {

    private static final Logger log = LoggerFactory.getLogger(SegmentedProcessLog.class);

    private final Path logsDir;

    public SegmentedProcessLog(Path logsDir, UUID instanceId, LogAppender appender, long logSteamMaxDelay) throws IOException {
        super(logsDir, instanceId, appender, logSteamMaxDelay);
        this.logsDir = logsDir;
    }

    @Override
    public void run(Supplier<Boolean> stopCondition) throws Exception {
        FileWatcher.FileReader fileReader = new FileWatcher.ByteArrayFileReader();

        FileWatcher.watch(logsDir, stopCondition, logSteamMaxDelay, new LogSegmentNameParser(), new FileWatcher.FileListener<Long>() {

            @Override
            public boolean onNewFile(Long id) {
                return true;
            }

            @Override
            public long onChanged(Long id, RandomAccessFile in) throws IOException {
                return fileReader.read(in, chunk -> {
                    LogStatsParser.Result result = LogStatsParser.parse(chunk.bytes(), chunk.len());
                    if (result.chunk() != null) {
                        boolean success = appender.appendLog(instanceId, id, result.chunk());
                        if (!success) {
                            return 0;
                        }
                    }
                    LogSegmentStats stats = result.stats();
                    if (stats != null) {
                        appender.updateSegment(instanceId, id, result.stats());
                        if (isFinal(stats.status())) {
                            return -1;
                        }
                    }
                    return result.readPos();
                });
            }
        });
    }

    @Override
    public void delete() {
        super.delete();
        try {
            IOUtils.deleteRecursively(logsDir);
        } catch (IOException e) {
            log.warn("delete -> error while removing a log directory: {}", logsDir);
        }
    }

    private static boolean isFinal(LogSegmentUpdateRequest.StatusEnum status) {
        return status != null && status != LogSegmentUpdateRequest.StatusEnum.RUNNING;
    }
}
