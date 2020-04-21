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

import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SegmentedProcessLog extends RedirectedProcessLog {

    private static final Logger log = LoggerFactory.getLogger(SegmentedProcessLog.class);

    private final Path logsDir;
    private final Map<Path, Long> segmentIds;

    public SegmentedProcessLog(Path logsDir, UUID instanceId, LogAppender appender, long logSteamMaxDelay) throws IOException {
        super(logsDir, instanceId, appender, logSteamMaxDelay);
        this.logsDir = logsDir;
        this.segmentIds = new HashMap<>();
    }

    @Override
    public void run(Supplier<Boolean> stopCondition) throws Exception {
        FileWatcher.watch(logsDir, stopCondition, logSteamMaxDelay, new FileWatcher.FileListener() {
            @Override
            public Result onNewFile(Path file, BasicFileAttributes attrs) {
                Segment segment = parse(file.getFileName().toString());
                if (segment == null) {
                    return Result.IGNORE;
                }

                Long id = appender.createSegment(instanceId, segment.getCorrelationId(), segment.getName(), new Date(attrs.creationTime().toMillis()));
                if (id == null) {
                    return Result.ERROR;
                }

                segmentIds.put(file, id);

                return Result.OK;
            }

            @Override
            public void onNewData(Path file, byte[] data, int len) {
                byte[] chunkBuffer = new byte[len];
                System.arraycopy(data, 0, chunkBuffer, 0, len);
                appender.appendLog(instanceId, segmentIds.get(file), chunkBuffer);
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

    private static Segment parse(String segmentFileName) {
        if (segmentFileName.length() < 36) {
            log.warn("createSegment ['{}'] -> invalid segment file name: no uuid", segmentFileName);
            return null;
        }

        try {
            UUID correlationId = UUID.fromString(segmentFileName.substring(0, 36));

            if (!segmentFileName.endsWith(".log")) {
                log.warn("createSegment ['{}'] -> invalid segment file name: invalid extension", segmentFileName);
                return null;
            }

            if (segmentFileName.length() - 37 - ".log".length() <= 0) {
                log.warn("createSegment ['{}'] -> invalid segment file name: no name", segmentFileName);
                return null;
            }
            String segmentName = segmentFileName.substring(37, segmentFileName.length() - ".log".length());

            return new Segment(correlationId, segmentName);
        } catch (Exception e) {
            log.warn("createSegment ['{}'] -> error: {}", segmentFileName, e.getMessage());
        }
        return null;
    }

    static class Segment {

        private final UUID correlationId;
        private final String name;

        public Segment(UUID correlationId, String name) {
            this.correlationId = correlationId;
            this.name = name;
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Segment{" +
                    "correlationId=" + correlationId +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
