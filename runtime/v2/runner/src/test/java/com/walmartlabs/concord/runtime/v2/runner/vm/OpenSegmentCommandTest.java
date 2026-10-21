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

import ch.qos.logback.classic.Level;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ParallelBlock;
import com.walmartlabs.concord.runtime.v2.model.ParallelBlockOptions;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OpenSegmentCommandTest {

    @Test
    public void parallelEvaluatesNameAndUsesExplicitLevel() {
        var context = openParallel(Map.of("segmentName", "Deploy ${env}", "logLevel", "WARN"), Level.ERROR);
        assertEquals("Deploy prod", context.segmentName());
        assertEquals(Level.WARN, context.logLevel());
    }

    @Test
    public void parallelWithoutMetadataInheritsLevelAndUsesStructuralName() {
        var context = openParallel(Map.of(), Level.ERROR);
        assertEquals("parallel", context.segmentName());
        assertEquals(Level.ERROR, context.logLevel());
    }

    @Test
    public void namedParallelStillInheritsLevel() {
        var context = openParallel(Map.of("segmentName", "Deploy ${env}"), Level.WARN);
        assertEquals("Deploy prod", context.segmentName());
        assertEquals(Level.WARN, context.logLevel());
    }

    private static LogContext openParallel(Map<String, Serializable> meta, Level parentLevel) {
        var step = new ParallelBlock(null, List.of(), ParallelBlockOptions.builder().meta(meta).build());
        var correlationId = UUID.randomUUID();
        var command = OpenSegmentCommand.parallel(step, correlationId);
        var state = new InMemoryState(Frame.builder().root().commands(command).build());
        var thread = state.getRootThreadId();
        var parent = LogContext.builder().segmentId(1L).segmentName("parent")
                .correlationId(UUID.randomUUID()).logLevel(parentLevel).redirectSystemOutAndErr(false).build();
        LogSegmentUtils.pushLogContext(thread, state, parent);
        Runtime runtime = mock(Runtime.class);
        var logger = mock(RunnerLogger.class);
        when(runtime.getService(RunnerLogger.class)).thenReturn(logger);
        when(logger.createSegment(anyString(), eq(correlationId), any())).thenReturn(2L);
        when(runtime.getService(RunnerConfiguration.class)).thenReturn(RunnerConfiguration.builder().build());
        var sensitive = mock(SensitiveDataHolder.class);
        when(runtime.getService(SensitiveDataHolder.class)).thenReturn(sensitive);
        when(sensitive.get()).thenReturn(Set.of());
        var factory = mock(ContextFactory.class);
        var ctx = mock(Context.class);
        when(runtime.getService(ContextFactory.class)).thenReturn(factory);
        when(factory.create(runtime, state, thread, step, correlationId)).thenReturn(ctx);
        when(ctx.eval("Deploy ${env}", String.class)).thenReturn("Deploy prod");
        command.eval(runtime, state, thread);
        var result = LogSegmentUtils.getLogContext(thread, state);
        assertNotNull(result);
        assertEquals(parentLevel, parent.logLevel());
        verify(logger).createSegment(eq(result.segmentName()), eq(correlationId),
                argThat(a -> a.parentId().equals(1L)));
        return result;
    }
}
