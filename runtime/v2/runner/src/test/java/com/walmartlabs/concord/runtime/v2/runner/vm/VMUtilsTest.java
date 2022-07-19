package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.InMemoryState;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VMUtilsTest {

    @Test
    public void testLocals() {
        Frame rootFrame = Frame.builder()
                .root()
                .locals(Collections.singletonMap("x", 123))
                .build();

        State state = new InMemoryState(rootFrame);

        ThreadId threadId = state.getRootThreadId();

        Frame levelOneFrame = Frame.builder().nonRoot()
                .locals(Collections.singletonMap("y", 234))
                .build();
        state.pushFrame(threadId, levelOneFrame);

        Frame levelTwoFrame = Frame.builder().nonRoot()
                .locals(Collections.singletonMap("x", 345))
                .build();
        state.pushFrame(threadId, levelTwoFrame);

        Map<String, Object> locals = VMUtils.getCombinedLocals(state, threadId);
        assertEquals(2, locals.size());
        assertEquals(345, locals.get("x"));
        assertEquals(234, locals.get("y"));
    }
}
