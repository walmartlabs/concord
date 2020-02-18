package com.walmartlabs.concord.runtime.v2.runner.vars;

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

import com.walmartlabs.concord.runtime.v2.runner.vm.VMUtils;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link GlobalVariables} implementation that knows how to pick up
 * frame-local overrides.
 * All changes are propagated into the delegate {@link GlobalVariables}.
 */
public class GlobalVariablesWithFrameOverrides implements GlobalVariables {

    private static final long serialVersionUID = 1L;

    private final State state;
    private final ThreadId threadId;
    private final GlobalVariables delegate;

    public GlobalVariablesWithFrameOverrides(State state, ThreadId threadId, GlobalVariables delegate) {
        this.state = state;
        this.threadId = threadId;
        this.delegate = delegate;
    }

    @Override
    public Object get(String key) {
        synchronized (state) {
            Map<String, Object> frameLocals = VMUtils.getLocalOverrides(state, threadId);
            if (frameLocals.containsKey(key)) {
                return frameLocals.get(key);
            }
        }

        return delegate.get(key);
    }

    @Override
    public void put(String key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public void putAll(Map<String, Object> values) {
        delegate.putAll(values);
    }

    @Override
    public Object remove(String key) {
        return delegate.remove(key);
    }

    @Override
    public boolean containsKey(String key) {
        synchronized (state) {
            Map<String, Object> frameLocals = VMUtils.getLocalOverrides(state, threadId);
            if (frameLocals.containsKey(key)) {
                return true;
            }
        }

        return delegate.containsKey(key);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>(delegate.toMap());

        Map<String, Object> frameLocals;
        synchronized (state) {
            frameLocals = VMUtils.getLocalOverrides(state, threadId);
        }
        result.putAll(frameLocals);

        return result;
    }
}
