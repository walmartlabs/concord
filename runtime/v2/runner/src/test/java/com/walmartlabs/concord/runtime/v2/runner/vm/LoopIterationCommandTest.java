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
import com.walmartlabs.concord.runtime.v2.model.ParallelBlock;
import com.walmartlabs.concord.runtime.v2.model.ParallelBlockOptions;
import com.walmartlabs.concord.runtime.v2.model.Retry;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LoopIterationCommandTest {

    @Test
    public void serializedFactoriesCreateIndependentIterations() throws Exception {
        var step = new ParallelBlock(null, List.of(), ParallelBlockOptions.builder().build());
        var correlationId = UUID.randomUUID();
        var body = new PopFrameCommand();
        var plain = restore(LoopIterationCommand.segment(body, step, correlationId));
        var retry = restore(LoopIterationCommand.retry(
                new RetryWrapper(body, Retry.builder().build(), step, correlationId)));
        // Create both commands before executing either: assigning the second index must not
        // mutate a shared retry command used by the first parallel iteration.
        var first = restore(retry.create(2));
        var second = restore(retry.create(7));
        assertNotSame(first, second);
        assertIndex(first, 2, LogSegmentKind.ATTEMPT);
        assertIndex(second, 7, LogSegmentKind.ATTEMPT);
        assertIndex(restore(plain.create(4)), 4, LogSegmentKind.ITERATION);
    }

    private static void assertIndex(Command command, int index, LogSegmentKind kind) throws Exception {
        var state = new InMemoryState(Frame.builder().root().commands(command).build());
        var thread = state.getRootThreadId();
        Runtime runtime = mock(Runtime.class);
        var logger = mock(RunnerLogger.class);
        when(runtime.getService(RunnerLogger.class)).thenReturn(logger);
        when(logger.createSegment(anyString(), any(), any())).thenReturn(1L);
        when(runtime.getService(RunnerConfiguration.class)).thenReturn(RunnerConfiguration.builder().build());
        var sensitive = mock(SensitiveDataHolder.class);
        when(runtime.getService(SensitiveDataHolder.class)).thenReturn(sensitive);
        when(sensitive.get()).thenReturn(Set.of());
        var factory = mock(ContextFactory.class);
        when(runtime.getService(ContextFactory.class)).thenReturn(factory);
        when(factory.create(eq(runtime), eq(state), eq(thread), any())).thenReturn(mock(Context.class));
        command.eval(runtime, state, thread);
        state.peekFrame(thread).peek().eval(runtime, state, thread);
        verify(logger).createSegment(anyString(), any(), argThat(a ->
                a.kind() == kind && Integer.valueOf(index).equals(a.iterationIndex())
                        && (kind == LogSegmentKind.ATTEMPT ? Integer.valueOf(0).equals(a.attemptIndex()) : a.attemptIndex() == null)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T restore(T value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
