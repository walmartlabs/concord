package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingClient;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SimpleLogger;
import com.walmartlabs.concord.svm.Runtime;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ExpressionLogContextTest {

    @Test
    public void usesExistingSystemSegmentWithoutCreatingOne() {
        var client = mock(LoggingClient.class);
        assertSystemContext(spy(new SegmentedLogger(client)), 0L);
        verifyNoInteractions(client);
    }

    @Test
    public void usesNoSegmentWhenSegmentedLoggingIsDisabled() {
        assertSystemContext(spy(new SimpleLogger()), null);
    }

    private static void assertSystemContext(RunnerLogger logger, Long expectedId) {
        Runtime runtime = mock(Runtime.class);
        when(runtime.getService(RunnerLogger.class)).thenReturn(logger);
        when(runtime.getService(RunnerConfiguration.class)).thenReturn(RunnerConfiguration.builder().build());
        var correlationId = UUID.randomUUID();
        var command = new ExpressionCommand(correlationId, null);
        var context = command.getDefaultLogContext(runtime, correlationId);
        assertEquals(expectedId, context.segmentId());
        assertEquals(SegmentedLogger.SYSTEM_SEGMENT_NAME, context.segmentName());
        assertEquals(correlationId, context.correlationId());
        verify(logger).getSystemSegmentId();
        verify(logger, never()).createSegment(any(), any(), any());
    }
}
