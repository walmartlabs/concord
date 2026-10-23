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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogChunk;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessLogResourceV2Test {

    @Test
    void pagesExposeBlockBoundariesAndRejectOverlappingCursors() {
        var log = new ProcessLog(120, List.of(
                new ProcessLogChunk(100, "first\n".getBytes(StandardCharsets.UTF_8)),
                new ProcessLogChunk(106, "next\n".getBytes(StandardCharsets.UTF_8))));
        var page = ProcessLogResourceV2.toPage(log, null, null);
        assertEquals("first\nnext\n", new String(java.util.Base64.getDecoder().decode(page.dataBase64()), StandardCharsets.UTF_8));
        assertEquals(100, page.before());
        assertEquals(111, page.after());
        assertTrue(page.hasPrevious());
        assertTrue(page.hasNext());
        assertEquals(page, ProcessLogResourceV2.toPage(log, 111, null));
        assertEquals(page, ProcessLogResourceV2.toPage(log, null, 100));
        assertThrows(ValidationErrorsException.class, () -> ProcessLogResourceV2.toPage(log, 110, null));
        assertThrows(ValidationErrorsException.class, () -> ProcessLogResourceV2.toPage(log, null, 101));
    }

    @Test
    void bytePagesPreserveSplitCharactersAcrossBlocksPagesAndConcurrentAppends() {
        var bytes = "Aé€😀Z".getBytes(StandardCharsets.UTF_8);
        var received = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            // A stored block can split a character; the size query may see newer bytes.
            var page = ProcessLogResourceV2.toPage(new ProcessLog(bytes.length + 1,
                    List.of(new ProcessLogChunk(i, new byte[]{bytes[i]}))), null, i);
            assertEquals(i, page.before());
            assertEquals(i + 1, page.after());
            received.writeBytes(java.util.Base64.getDecoder().decode(page.dataBase64()));
        }
        assertArrayEquals(bytes, received.toByteArray());
        assertEquals("Aé€😀Z", received.toString(StandardCharsets.UTF_8));
        var together = ProcessLogResourceV2.toPage(new ProcessLog(3, List.of(
                new ProcessLogChunk(0, new byte[]{(byte) 0xe2}),
                new ProcessLogChunk(1, new byte[]{(byte) 0x82, (byte) 0xac}))), null, null);
        assertArrayEquals("€".getBytes(StandardCharsets.UTF_8), java.util.Base64.getDecoder().decode(together.dataBase64()));
        var backwards = ProcessLogResourceV2.toPage(new ProcessLog(3,
                List.of(new ProcessLogChunk(1, new byte[]{(byte) 0x82, (byte) 0xac}))), 3, null);
        assertArrayEquals(new byte[]{(byte) 0x82, (byte) 0xac}, java.util.Base64.getDecoder().decode(backwards.dataBase64()));
    }

    @Test
    void emptyPagesNeverJumpOverConcurrentAppends() {
        var log = new ProcessLog(200, List.of());
        var initial = ProcessLogResourceV2.toPage(log, null, null);
        assertEquals(0, initial.after());
        assertFalse(initial.hasNext());
        var append = ProcessLogResourceV2.toPage(log, null, 100);
        assertEquals(100, append.after());
        assertFalse(append.hasNext());
    }

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
