package com.walmartlabs.concord.runtime.v2.runner.script;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptResult {

    private final Map<String, Object> items = new ConcurrentHashMap<>();

    public ScriptResult set(String key, Object value) {
        Object sanitized = VariablesSanitizer.sanitize(value);
        items.put(key, sanitized);
        return this;
    }

    public Map<String, Object> items() {
        return items;
    }
}
