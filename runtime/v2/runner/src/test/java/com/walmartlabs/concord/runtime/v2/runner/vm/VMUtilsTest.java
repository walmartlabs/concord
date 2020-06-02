package com.walmartlabs.concord.runtime.v2.runner.vm;

import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.InMemoryState;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
