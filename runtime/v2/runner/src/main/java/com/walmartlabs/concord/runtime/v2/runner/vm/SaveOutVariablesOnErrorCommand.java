package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.OutVariablesProcessor;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serial;

public class SaveOutVariablesOnErrorCommand implements Command {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        runtime.getService(OutVariablesProcessor.class).afterProcessEnds(runtime, state, frame);
    }
}
