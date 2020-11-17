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

public class LogSegmentNameParser implements FileWatcher.FileNameParser<Long> {

    private static final Logger log = LoggerFactory.getLogger(LogSegmentNameParser.class);

    private static final String LOG_EXT = ".log";

    public Long parse(Path path) {
        String segmentFileName = path.getFileName().toString();

        // move agent process logs into system segment
        if ("system.log".equals(segmentFileName)) {
            return 0L;
        }

        if (!segmentFileName.endsWith(LOG_EXT)) {
            log.warn("createSegment ['{}'] -> invalid segment file name: invalid extension", segmentFileName);
            return null;
        }

        if (segmentFileName.length() <= LOG_EXT.length()) {
            log.warn("createSegment ['{}'] -> invalid segment file name", segmentFileName);
            return null;
        }

        try {
            return Long.valueOf(segmentFileName.substring(0, segmentFileName.length() - LOG_EXT.length()));
        } catch (Exception e) {
            log.warn("createSegment ['{}'] -> error: {}", segmentFileName, e.getMessage());
        }

        return null;
    }
}
