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

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModeAwareProcessLogTest {

    @Test
    public void buffersMessagesUntilSegmentedModeIsKnown() {
        var appender = new RecordingLogAppender();
        var processLog = new ModeAwareProcessLog(UUID.randomUUID(), appender, 0L);

        processLog.info("queued segmented message");

        assertEquals("", appender.systemLog());
        assertEquals("", appender.segmentLog(0));

        processLog.setSegmented(true);

        assertEquals("", appender.systemLog());
        assertTrue(appender.segmentLog(0).contains("queued segmented message"));
    }

    @Test
    public void buffersMessagesUntilNonSegmentedModeIsKnown() {
        var appender = new RecordingLogAppender();
        var processLog = new ModeAwareProcessLog(UUID.randomUUID(), appender, 0L);

        processLog.warn("queued plain message");

        assertEquals("", appender.systemLog());

        processLog.setSegmented(false);

        assertTrue(appender.systemLog().contains("queued plain message"));
        assertEquals("", appender.segmentLog(0));
    }

    @Test
    public void fallsBackToSystemLogIfAnErrorOccursBeforeModeIsConfigured() {
        var appender = new RecordingLogAppender();
        var processLog = new ModeAwareProcessLog(UUID.randomUUID(), appender, 0L);

        processLog.info("queued startup message");
        processLog.error("orphaned startup message");

        assertTrue(appender.systemLog().contains("queued startup message"));
        assertTrue(appender.systemLog().contains("orphaned startup message"));
        assertEquals("", appender.segmentLog(0));
    }
}
