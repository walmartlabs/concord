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

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.LogSegmentUpdateRequest;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessLogV2Api;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.agent.logging.ProcessLogTransport.DeliveryStatus.DELIVERED;
import static com.walmartlabs.concord.agent.logging.ProcessLogTransport.DeliveryStatus.FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProcessLogTransportTest {

    @Test
    public void remoteTransportSendsSystemBytes() throws Exception {
        var instanceId = UUID.randomUUID();
        var processApi = mock(ProcessApi.class);
        var processLogV2Api = mock(ProcessLogV2Api.class);
        var transport = new RemoteProcessLogTransport(instanceId, processApi, processLogV2Api);
        var input = "system log".getBytes(StandardCharsets.UTF_8);

        assertEquals(DELIVERED, transport.appendSystem(input));

        var payload = ArgumentCaptor.forClass(InputStream.class);
        verify(processApi).appendProcessLog(eq(instanceId), payload.capture());
        assertEquals("system log", new String(payload.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    public void remoteTransportSendsSegmentBytesAndStats() throws Exception {
        var instanceId = UUID.randomUUID();
        var processApi = mock(ProcessApi.class);
        var processLogV2Api = mock(ProcessLogV2Api.class);
        var transport = new RemoteProcessLogTransport(instanceId, processApi, processLogV2Api);
        var stats = new LogSegmentStats(LogSegmentStatus.OK, 2, 1);
        var input = "segment log".getBytes(StandardCharsets.UTF_8);

        assertEquals(DELIVERED, transport.appendSegment(7, input));
        assertEquals(DELIVERED, transport.updateSegment(7, stats));

        var payload = ArgumentCaptor.forClass(InputStream.class);
        verify(processLogV2Api).appendProcessLogSegment(eq(instanceId), eq(7L), payload.capture());
        assertEquals("segment log", new String(payload.getValue().readAllBytes(), StandardCharsets.UTF_8));

        var request = ArgumentCaptor.forClass(LogSegmentUpdateRequest.class);
        verify(processLogV2Api).updateProcessLogSegment(eq(instanceId), eq(7L), request.capture());
        assertEquals(LogSegmentUpdateRequest.StatusEnum.OK, request.getValue().getStatus());
        assertEquals(2, request.getValue().getErrors());
        assertEquals(1, request.getValue().getWarnings());
    }

    @Test
    public void remoteTransportReturnsFailedOnApiErrors() throws Exception {
        var instanceId = UUID.randomUUID();
        var processApi = mock(ProcessApi.class);
        var processLogV2Api = mock(ProcessLogV2Api.class);
        var transport = new RemoteProcessLogTransport(instanceId, processApi, processLogV2Api);
        var error = new ApiException(400, "boom");

        doThrow(error).when(processApi).appendProcessLog(eq(instanceId), any(InputStream.class));
        doThrow(error).when(processLogV2Api).appendProcessLogSegment(eq(instanceId), eq(3L), any(InputStream.class));
        when(processLogV2Api.updateProcessLogSegment(eq(instanceId), eq(3L), any(LogSegmentUpdateRequest.class))).thenThrow(error);

        assertEquals(FAILED, transport.appendSystem("system".getBytes(StandardCharsets.UTF_8)));
        assertEquals(FAILED, transport.appendSegment(3, "segment".getBytes(StandardCharsets.UTF_8)));
        assertEquals(FAILED, transport.updateSegment(3, new LogSegmentStats(LogSegmentStatus.ERROR, 1, 0)));
    }

    @Test
    public void stdoutMirrorTransportMirrorsSystemAndSegmentBytes() {
        var stdout = new ByteArrayOutputStream();
        var out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        var transport = new StdoutMirrorTransport(out);

        assertEquals(DELIVERED, transport.appendSystem("worker".getBytes(StandardCharsets.UTF_8)));
        assertEquals(DELIVERED, transport.appendSegment(1, "runner".getBytes(StandardCharsets.UTF_8)));
        assertEquals(DELIVERED, transport.updateSegment(1, new LogSegmentStats(LogSegmentStatus.OK, 0, 0)));

        var mirrored = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(mirrored.contains("RUNNER: worker"));
        assertTrue(mirrored.contains("RUNNER: runner"));
    }

    @Test
    public void compositeTransportFansOutAndFailsIfAnyTransportFails() {
        var first = mock(ProcessLogTransport.class);
        var second = mock(ProcessLogTransport.class);
        var stats = new LogSegmentStats(LogSegmentStatus.RUNNING, 0, 0);
        var payload = "payload".getBytes(StandardCharsets.UTF_8);
        var transport = new CompositeProcessLogTransport(Set.of(first, second));

        when(first.appendSystem(payload)).thenReturn(DELIVERED);
        when(second.appendSystem(payload)).thenReturn(FAILED);
        when(first.appendSegment(5, payload)).thenReturn(DELIVERED);
        when(second.appendSegment(5, payload)).thenReturn(DELIVERED);
        when(first.updateSegment(5, stats)).thenReturn(DELIVERED);
        when(second.updateSegment(5, stats)).thenReturn(DELIVERED);

        assertEquals(FAILED, transport.appendSystem(payload));
        assertEquals(DELIVERED, transport.appendSegment(5, payload));
        assertEquals(DELIVERED, transport.updateSegment(5, stats));

        verify(first).appendSystem(payload);
        verify(second).appendSystem(payload);
        verify(first).appendSegment(5, payload);
        verify(second).appendSegment(5, payload);
        verify(first).updateSegment(5, stats);
        verify(second).updateSegment(5, stats);
    }
}
