package com.walmartlabs.concord.svm;

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

public class PopFrameCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final boolean skipFinallyHandler;

    public PopFrameCommand() {
        this(false);
    }

    private PopFrameCommand(boolean ignoreFinallyHandler) {
        this.skipFinallyHandler = ignoreFinallyHandler;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        if (skipFinallyHandler) {
            popFrame(state, threadId);

            return;
        }

        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Command finallyHandler = frame.getFinallyHandler();

        if (finallyHandler == null) {
            // no 'finally' block, just pop the frame
            popFrame(state, threadId);

            return;
        }

        // actually pop the frame
        frame.push(new PopFrameCommand(true));

        // execute the 'finally' block
        frame.push(finallyHandler);
    }

    private static void popFrame(State state, ThreadId threadId) {
        state.popFrame(threadId);

        if (state.getStatus(threadId) == ThreadStatus.UNWINDING) {
            Frame f = state.peekFrame(threadId);
            if (f != null) {
                f.push(new PopFrameCommand());
            }
        }
    }
}
