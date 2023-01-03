package com.walmartlabs.concord.plugins.slack;

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

import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.Context;

import java.util.Map;

public class ContextVariables implements Variables {

    private final Context context;

    public ContextVariables(Context context) {
        this.context = context;
    }

    @Override
    public Object get(String key) {
        return context.getVariable(key);
    }

    @Override
    public void set(String key, Object value) {
        throw new IllegalStateException("Unsupported");
    }

    @Override
    public boolean has(String key) {
        return context.getVariable(key) != null;
    }

    @Override
    public Map<String, Object> toMap() {
        return context.toMap();
    }
}
