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

import com.walmartlabs.concord.runtime.v2.model.Loop;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.InMemoryState;
import com.walmartlabs.concord.svm.Runtime;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class LoopWrapperTest {

    @Test
    public void testEmptyItemsAreEvaluatedBeforeDynamicOutput() {
        var fixture = new Fixture("${loopItems}");
        when(fixture.evaluator.eval(fixture.context, "${loopItems}", Serializable.class)).thenReturn(new ArrayList<>());
        when(fixture.evaluator.eval(fixture.context, "${loopOut}", Object.class)).thenReturn(List.of("result"));

        fixture.run();

        var order = inOrder(fixture.evaluator);
        order.verify(fixture.evaluator).eval(fixture.context, "${loopItems}", Serializable.class);
        order.verify(fixture.evaluator).eval(fixture.context, "${loopOut}", Object.class);
        order.verifyNoMoreInteractions();
        assertEquals(List.of(), fixture.root.getLocal("result"));
    }

    @Test
    public void testItemsFailureLeavesOutputUntouched() {
        var fixture = new Fixture("${loopItems}");
        var failure = new IllegalStateException("items failed");
        when(fixture.evaluator.eval(fixture.context, "${loopItems}", Serializable.class)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, fixture::run));
        verify(fixture.evaluator).eval(fixture.context, "${loopItems}", Serializable.class);
        verifyNoMoreInteractions(fixture.evaluator);
        assertEquals("seed", fixture.root.getLocal("result"));
    }

    private static final class Fixture {
        private final Runtime runtime = mock(Runtime.class);
        private final ExpressionEvaluator evaluator = mock(ExpressionEvaluator.class);
        private final EvalContext context = mock(EvalContext.class);
        private final Frame root = Frame.builder().root().build();
        private final InMemoryState state = new InMemoryState(root);
        private final LoopWrapper wrapper;

        private Fixture(Serializable items) {
            var contextFactory = mock(ContextFactory.class);
            var evalContextFactory = mock(EvalContextFactory.class);
            var runtimeContext = mock(Context.class);
            var loop = mock(Loop.class);
            when(loop.items()).thenReturn(items);
            when(loop.mode()).thenReturn(Loop.Mode.SERIAL);
            when(runtime.getService(ContextFactory.class)).thenReturn(contextFactory);
            when(runtime.getService(EvalContextFactory.class)).thenReturn(evalContextFactory);
            when(runtime.getService(ExpressionEvaluator.class)).thenReturn(evaluator);
            when(contextFactory.create(runtime, state, state.getRootThreadId(), null)).thenReturn(runtimeContext);
            when(evalContextFactory.global(runtimeContext)).thenReturn(context);

            wrapper = LoopWrapper.of(null, (r, s, t) -> { throw new AssertionError("empty loop body ran"); },
                    loop, List.of(), null, "${loopOut}", null);
            root.setLocal("result", "seed");
            root.push(wrapper);
        }

        private void run() {
            wrapper.eval(runtime, state, state.getRootThreadId());
        }
    }
}
