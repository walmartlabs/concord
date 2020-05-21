package com.walmartlabs.concord.runtime.v2.runner.context;

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

import com.walmartlabs.concord.runtime.v2.runner.vm.VMUtils;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class ContextVariables implements Variables {

    private final Context ctx;

    public ContextVariables(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public Object get(String key) {
        return VMUtils.mapLocals(frames(), key, Function.identity());
    }

    @Override
    public boolean has(String key) {
        return VMUtils.mapLocals(frames(), key, Objects::nonNull);
    }

    @Override
    public void set(String key, Object value) {
        ThreadId threadId = ctx.execution().currentThreadId();
        State state = ctx.execution().state();
        Frame frame = state.peekFrame(threadId);
        VMUtils.putLocal(frame, key, value);
    }

    @Override
    public Map<String, Object> toMap() {
        return VMUtils.getLocals(ctx);
    }

    private List<Frame> frames() {
        ThreadId threadId = ctx.execution().currentThreadId();
        State state = ctx.execution().state();
        return state.getFrames(threadId);
    }
}
