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

import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GlobalVariablesImpl implements GlobalVariables {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> vars;

    public GlobalVariablesImpl() {
        this(Collections.emptyMap());
    }

    public GlobalVariablesImpl(Map<String, Object> initial) {
        this.vars = new HashMap<>(initial);
    }

    @Override
    public Object get(String key) {
        synchronized (vars) {
            return vars.get(key);
        }
    }

    @Override
    public void put(String key, Object value) {
        synchronized (vars) {
            vars.put(key, value);
        }
    }

    @Override
    public void putAll(Map<String, Object> values) {
        synchronized (vars) {
            vars.putAll(values);
        }
    }

    @Override
    public Object remove(String key) {
        synchronized (vars) {
            return vars.remove(key);
        }
    }

    @Override
    public boolean containsKey(String key) {
        synchronized (vars) {
            return vars.containsKey(key);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        synchronized (vars) {
            return new HashMap<>(vars);
        }
    }
}
