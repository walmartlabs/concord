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

import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ExecutionLifecycleTest {

    @Test
    public void throwingFrameListenerDoesNotPreventCompletion() {
        var notifications = new AtomicInteger();
        Runtime runtime = mock(Runtime.class);
        var vm = new VM(v -> runtime, List.of(throwingListener(), new ExecutionListener() {
            @Override
            public void beforeFramePop(Runtime r, VM v, State s, ThreadId t, Frame f) {
                notifications.incrementAndGet();
            }
        }));
        var state = new InMemoryState(Frame.builder().root().build());
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> vm.eval(runtime, state, state.getRootThreadId()));
        assertEquals(1, notifications.get());
        // eval collects completed threads before returning.
        assertNull(state.peekFrame(state.getRootThreadId()));
    }

    @Test
    public void throwingListenersPreserveOriginalErrorAndRunHandler() {
        var original = new IllegalStateException("original step error");
        var notifications = new AtomicInteger();
        var handled = new AtomicInteger();
        Runtime runtime = mock(Runtime.class);
        var vm = new VM(v -> runtime, List.of(throwingListener(), new ExecutionListener() {
            @Override
            public void beforeExceptionHandler(Runtime r, VM v, State s, ThreadId t, Frame f) {
                assertSame(original, s.getThreadError(t).exception());
                notifications.incrementAndGet();
            }
        }));
        Command fail = (r, s, t) -> { throw original; };
        Command handler = (r, s, t) -> {
            Frame frame = s.peekFrame(t);
            assertSame(original, frame.getLocal(Frame.LAST_EXCEPTION_KEY));
            frame.pop();
            handled.incrementAndGet();
        };
        var state = new InMemoryState(Frame.builder().root().commands(fail).exceptionHandler(handler).build());
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> vm.eval(runtime, state, state.getRootThreadId()));
        assertEquals(1, notifications.get());
        assertEquals(1, handled.get());
        // eval collects completed threads before returning.
        assertNull(state.peekFrame(state.getRootThreadId()));
    }

    @Test
    public void throwingFrameListenerDoesNotReplaceUnhandledError() {
        var original = new IllegalStateException("unhandled step error");
        Runtime runtime = mock(Runtime.class);
        var vm = new VM(v -> runtime, List.of(throwingListener()));
        Command fail = (r, s, t) -> { throw original; };
        var state = new InMemoryState(Frame.builder().root().commands(fail).build());
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertSame(original, assertThrows(IllegalStateException.class,
                        () -> vm.eval(runtime, state, state.getRootThreadId()))));
    }

    private static ExecutionListener throwingListener() {
        return new ExecutionListener() {
            @Override
            public void beforeFramePop(Runtime r, VM v, State s, ThreadId t, Frame f) {
                throw new IllegalStateException("broken frame listener");
            }

            @Override
            public void beforeExceptionHandler(Runtime r, VM v, State s, ThreadId t, Frame f) {
                throw new IllegalStateException("broken handler listener");
            }
        };
    }
}
