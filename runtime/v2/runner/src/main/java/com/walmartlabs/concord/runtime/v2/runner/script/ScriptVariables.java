package com.walmartlabs.concord.runtime.v2.runner.script;

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

import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Map;

public class ScriptVariables implements Variables {

    private final Variables delegate;

    public ScriptVariables(Variables delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object get(String key) {
        return delegate.get(key);
    }

    @Override
    public void set(String key, Object value) {
        Object sanitized = VariablesSanitizer.sanitize(value);
        delegate.set(key, sanitized);
    }

    @Override
    public boolean has(String key) {
        return delegate.has(key);
    }

    @Override
    public Map<String, Object> toMap() {
        return delegate.toMap();
    }
}