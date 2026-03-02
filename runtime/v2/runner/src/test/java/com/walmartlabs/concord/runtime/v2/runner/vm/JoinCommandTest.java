package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.svm.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JoinCommandTest {

    @Test
    public void testFailedThreadsShouldBeFilteredByOwnedIds() {
        // Setup state with a root frame for the parent thread
        Frame rootFrame = Frame.builder().root().build();
        InMemoryState state = new InMemoryState(rootFrame);

        ThreadId parentThread = state.getRootThreadId();   // thread 0
        ThreadId ownedThread1 = state.nextThreadId();      // thread 1
        ThreadId ownedThread2 = state.nextThreadId();      // thread 2
        ThreadId foreignThread = state.nextThreadId();     // thread 3

        // ownedThread1 completed successfully
        state.setStatus(ownedThread1, ThreadStatus.DONE);
        // ownedThread2 failed (owned by this JoinCommand)
        state.setStatus(ownedThread2, ThreadStatus.FAILED);
        // foreignThread failed (NOT owned by this JoinCommand — belongs to another parallel block)
        state.setStatus(foreignThread, ThreadStatus.FAILED);

        Exception ownedError = new RuntimeException("owned thread error");
        Exception foreignError = new RuntimeException("foreign thread error");
        state.setThreadError(ownedThread2, ownedError);
        state.setThreadError(foreignThread, foreignError);

        // JoinCommand only owns threads 1 and 2
        List<ThreadId> ids = List.of(ownedThread1, ownedThread2);
        JoinCommand<Step> joinCommand = new JoinCommand<>(ids, null);

        // Execute — should throw ParallelExecutionException for the owned failed thread only
        ParallelExecutionException exception = assertThrows(
                ParallelExecutionException.class,
                () -> joinCommand.execute(null, state, parentThread));

        // The exception should contain ONLY the owned thread's error
        assertEquals(1, exception.getExceptions().size(),
                "Should contain only the owned thread's error, not the foreign thread's error");
        assertSame(ownedError, exception.getExceptions().get(0));

        // The foreign thread's error should NOT have been cleared
        assertNotNull(state.getThreadError(foreignThread),
                "Foreign thread's error should remain in state (not cleared by this JoinCommand)");
        assertSame(foreignError, state.getThreadError(foreignThread).exception(),
                "Foreign thread's error should be untouched");
    }
}
