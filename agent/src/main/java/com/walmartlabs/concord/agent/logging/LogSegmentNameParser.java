package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;

public class LogSegmentNameParser implements FileWatcher.FileNameParser<LogSegment> {

    private static final Logger log = LoggerFactory.getLogger(LogSegmentNameParser.class);

    public LogSegment parse(Path path) {
        int UUID_LEN = 36;

        String segmentFileName = path.getFileName().toString();

        // move agent process logs and process logs into system segment
        if ("system.log".equals(segmentFileName)
                || "runner_system.log".equalsIgnoreCase(segmentFileName)) {
            return LogSegment.builder()
                    .name("system")
                    .build();
        }

        if (!segmentFileName.endsWith(".log")) {
            log.warn("createSegment ['{}'] -> invalid segment file name: invalid extension", segmentFileName);
            return null;
        }

        if (segmentFileName.length() < UUID_LEN) {
            log.warn("createSegment ['{}'] -> invalid segment file name: no uuid", segmentFileName);
            return null;
        }

        try {
            UUID correlationId = UUID.fromString(segmentFileName.substring(0, UUID_LEN));

            int taskNameStartAt = UUID_LEN + 1;
            int taskNameEndAt = segmentFileName.lastIndexOf('_');
            if (taskNameEndAt < taskNameStartAt) {
                log.warn("createSegment ['{}'] -> invalid segment file name: no created at", segmentFileName);
                return null;
            }
            String segmentName = segmentFileName.substring(taskNameStartAt, taskNameEndAt);

            int createdAtStart = taskNameEndAt + 1;
            int createdAtEnd = segmentFileName.length() - ".log".length();
            long createdAt = Long.parseLong(segmentFileName.substring(createdAtStart, createdAtEnd));

            return LogSegment.builder()
                    .correlationId(correlationId)
                    .name(segmentName)
                    .createdAt(new Date(createdAt))
                    .build();
        } catch (Exception e) {
            log.warn("createSegment ['{}'] -> error: {}", segmentFileName, e.getMessage());
        }

        return null;
    }
}
