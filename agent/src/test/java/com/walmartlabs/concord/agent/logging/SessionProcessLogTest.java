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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionProcessLogTest {

    @Test
    public void nonSegmentedAgentMessagesUseSystemOutput() {
        var transport = new RecordingProcessLogTransport();
        var processLog = new SessionProcessLog(
                new DefaultProcessLogSession(new PlainOutputDecoder(transport)),
                5L);

        processLog.info("worker-stage message");

        assertTrue(transport.systemLog().contains("worker-stage message"));
        assertEquals("", transport.segmentLog(0));
    }

    @Test
    public void segmentedAgentMessagesUseSystemSegmentZero() {
        var transport = new RecordingProcessLogTransport();
        var processLog = new SessionProcessLog(
                new DefaultProcessLogSession(new SegmentedOutputDecoder(transport)),
                5L);

        processLog.warn("worker-stage message");

        assertEquals("", transport.systemLog());
        assertTrue(transport.segmentLog(0).contains("worker-stage message"));
    }

    @Test
    public void segmentedRawOutputFallsBackToSystemSegmentZero() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var processLog = new SessionProcessLog(
                new DefaultProcessLogSession(new SegmentedOutputDecoder(transport)),
                0L);

        processLog.log(new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8)));

        assertEquals("trash", transport.segmentLog(0));
    }
}
