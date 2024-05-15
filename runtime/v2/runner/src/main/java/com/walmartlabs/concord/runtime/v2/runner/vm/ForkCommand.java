package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Arrays;
import java.util.Map;

public class ForkCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final ThreadId childThreadId;
    private final Command[] cmds;

    public ForkCommand(ThreadId childThreadId, Command... cmds) {
        this.childThreadId = childThreadId;
        this.cmds = cmds;
    }

    @Override
    public Command copy() {
        return new ForkCommand(childThreadId,
                Arrays.stream(cmds).map(Command::copy).toArray(Command[]::new));
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        // create a new root frame
        state.fork(threadId, childThreadId, cmds);

        // copy all "in" variables
        Frame targetFrame = state.peekFrame(childThreadId);
        Map<String, Object> locals = VMUtils.getCombinedLocals(state, threadId);
        VMUtils.putLocals(targetFrame, locals);

        // run the new thread
        runtime.spawn(state, childThreadId);
    }
}
