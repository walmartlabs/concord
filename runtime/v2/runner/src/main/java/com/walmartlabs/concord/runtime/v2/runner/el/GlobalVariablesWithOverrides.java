package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple map-backed implementation of {@link GlobalVariables}.
 * Comparing to other implementations, this one doesn't do any defensive copying
 * so that the underlying map with override values can be modified externally.
 * All modifications are propagated to the delegated {@link GlobalVariables} instance.
 */
public class GlobalVariablesWithOverrides implements GlobalVariables {

    private static final long serialVersionUID = 1L;

    private final GlobalVariables delegate;
    private final Map<String, Object> overrides;

    public GlobalVariablesWithOverrides(GlobalVariables delegate, Map<String, Object> overrides) {
        this.delegate = delegate;
        this.overrides = overrides;
    }

    @Override
    public Object get(String key) {
        if (overrides.containsKey(key)) {
            return overrides.get(key);
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
        if (overrides.containsKey(key)) {
            return true;
        }
        return delegate.containsKey(key);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>(delegate.toMap());
        result.putAll(overrides);
        return result;
    }
}
