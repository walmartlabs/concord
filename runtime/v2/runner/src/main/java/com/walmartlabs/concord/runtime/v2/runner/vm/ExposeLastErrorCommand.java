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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

/**
 * Provides {@code ${lastError}} variable for {@code error} blocks.
 */
public class ExposeLastErrorCommand implements Command {

    private static final long serialVersionUID = 1L;

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Exception cause = (Exception) frame.getLocal(Frame.LAST_EXCEPTION_KEY);
        frame.setLocal(Constants.Context.LAST_ERROR_KEY, cause);
    }
}
