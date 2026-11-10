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

import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessLogResourceV2Test {

    /**
     * The status time comes from the runner: the agent delivers statuses with a delay.
     */
    @Test
    void storesTheStatusTimeOfTheRunner() {
        UUID instanceId = UUID.randomUUID();
        ProcessKey processKey = new ProcessKey(instanceId, OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));

        ProcessLogAccessManager logAccessManager = mock(ProcessLogAccessManager.class);
        when(logAccessManager.assertLogAccess(instanceId)).thenReturn(processKey);
        ProcessLogManager logManager = mock(ProcessLogManager.class);

        OffsetDateTime statusUpdatedAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 123_000_000, ZoneOffset.UTC);
        LogSegmentUpdateRequest request = ImmutableLogSegmentUpdateRequest.builder()
                .status(LogSegment.Status.OK)
                .statusUpdatedAt(statusUpdatedAt)
                .build();

        new ProcessLogResourceV2(null, logManager, logAccessManager, null).updateSegment(instanceId, 1, request);

        verify(logManager).updateSegment(processKey, 1, LogSegment.Status.OK, null, null, statusUpdatedAt);
    }
}
