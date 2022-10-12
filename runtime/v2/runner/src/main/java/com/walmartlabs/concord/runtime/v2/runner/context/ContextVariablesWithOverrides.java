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

import com.walmartlabs.concord.runtime.v2.runner.context.ContextVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.HashMap;
import java.util.Map;

public class ContextVariablesWithOverrides implements Variables {

    private final Variables contextVariables;
    private final Variables overrides;

    public ContextVariablesWithOverrides(Context context, Map<String, Object> overrides) {
        this.contextVariables = new ContextVariables(context);
        this.overrides = new MapBackedVariables(overrides);
    }

    @Override
    public Object get(String key) {
        if (overrides.has(key)) {
            return overrides.get(key);
        }
        return contextVariables.get(key);
    }

    @Override
    public void set(String key, Object value) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public boolean has(String key) {
        return overrides.has(key) || contextVariables.has(key);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> a = contextVariables.toMap();
        Map<String, Object> b = overrides.toMap();
        Map<String, Object> result = new HashMap<>(a);
        result.putAll(b);
        return result;
    }
}
