package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.HttpUtils;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessLogResourceV2Test {

    @Test
    void returnsTheActualCursorForAnEmptySuffixOfAnEmptyLog() {
        ProcessLog log = new ProcessLog(0, List.of());
        HttpUtils.Range range = HttpUtils.Range.builder().end(100).build();

        try (Response response = ProcessLogResourceV2.toResponse(UUID.randomUUID(), 0, log, range)) {
            assertEquals("bytes 0-0/0", response.getHeaderString("Content-Range"));
        }
    }

    @Test
    void returnsTheActualCursorForAnOffsetBeyondTheLog() {
        ProcessLog log = new ProcessLog(12, List.of());
        HttpUtils.Range range = HttpUtils.Range.builder().start(20).build();

        try (Response response = ProcessLogResourceV2.toResponse(UUID.randomUUID(), 0, log, range)) {
            assertEquals("bytes 12-12/12", response.getHeaderString("Content-Range"));
        }
    }

    /**
     * No chunks for a suffix means the log was empty when the chunks were read.
     */
    @Test
    void startsAnEmptySuffixAtZeroWhenTheLogGrowsAfterTheChunksAreRead() {
        ProcessLog log = new ProcessLog(150, List.of());
        HttpUtils.Range range = HttpUtils.Range.builder().end(100).build();

        try (Response response = ProcessLogResourceV2.toResponse(UUID.randomUUID(), 0, log, range)) {
            assertEquals("bytes 0-0/150", response.getHeaderString("Content-Range"));
        }
    }

    /**
     * The size is read after the chunks, so it can include data appended in between.
     * The cursor must not move past the bytes the response didn't return.
     */
    @Test
    void keepsTheRequestedCursorWhenTheLogGrowsAfterTheChunksAreRead() {
        UUID instanceId = UUID.randomUUID();
        ProcessKey processKey = new ProcessKey(instanceId, OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));

        ProcessLogAccessManager logAccessManager = mock(ProcessLogAccessManager.class);
        when(logAccessManager.assertLogAccess(instanceId)).thenReturn(processKey);

        ProcessLogManager logManager = mock(ProcessLogManager.class);
        when(logManager.segmentData(processKey, 1, 100, null)).thenReturn(new ProcessLog(150, List.of()));

        ProcessLogResourceV2 resource = new ProcessLogResourceV2(null, logManager, logAccessManager, null);

        try (Response response = resource.data(instanceId, 1, "bytes=100-")) {
            assertEquals("bytes 100-100/150", response.getHeaderString("Content-Range"));
        }
    }
}
