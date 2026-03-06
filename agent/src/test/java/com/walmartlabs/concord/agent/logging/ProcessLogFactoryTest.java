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

import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProcessLogFactoryTest {

    @Test
    public void createRunnerLogPreservesNonSegmentedMessageDelivery() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(appender).createRunnerLog(UUID.randomUUID(), false);

        log.info("plain factory message");

        assertTrue(appender.systemLog().contains("plain factory message"));
        assertEquals("", appender.segmentLog(0));
    }

    @Test
    public void createRunnerLogRoutesSegmentedMessagesToSystemSegmentZero() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(appender).createRunnerLog(UUID.randomUUID(), true);

        log.error("segmented factory message");

        assertEquals("", appender.systemLog());
        assertTrue(appender.segmentLog(0).contains("segmented factory message"));
    }

    @Test
    public void createRunnerLogStreamsPlainOutputIntoSystemLog() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(appender).createRunnerLog(UUID.randomUUID(), false);

        log.log(new ByteArrayInputStream("runner plain output".getBytes(StandardCharsets.UTF_8)));

        assertTrue(appender.systemLog().contains("runner plain output"));
        assertEquals("", appender.segmentLog(0));
    }

    @Test
    public void createRunnerLogStreamsUnframedSegmentedOutputIntoSystemSegmentZero() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(appender).createRunnerLog(UUID.randomUUID(), true);

        log.log(new ByteArrayInputStream("runner segmented output".getBytes(StandardCharsets.UTF_8)));

        assertEquals("", appender.systemLog());
        assertEquals("runner segmented output", appender.segmentLog(0));
    }

    private static ProcessLogFactory newFactory(LogAppender appender) throws Exception {
        var cfg = mock(AgentConfiguration.class);
        when(cfg.getLogMaxDelay()).thenReturn(5L);
        return new ProcessLogFactory(cfg, appender);
    }
}
