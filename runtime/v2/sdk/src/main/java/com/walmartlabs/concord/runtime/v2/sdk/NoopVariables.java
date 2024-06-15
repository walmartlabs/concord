package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.util.Collections;
import java.util.Map;

public class NoopVariables implements Variables {

    @Override
    public Object get(String key) {
        return null;
    }

    @Override
    public void set(String key, Object value) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public boolean has(String key) {
        return false;
    }

    @Override
    public Map<String, Object> toMap() {
        return Collections.emptyMap();
    }
}
