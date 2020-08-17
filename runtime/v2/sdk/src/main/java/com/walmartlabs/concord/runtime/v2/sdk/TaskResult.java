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

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TaskResult implements Serializable {

    public static TaskResult success() {
        return new TaskResult(true, null, null);
    }

    public static TaskResult error(String message) {
        return new TaskResult(false, message, null);
    }

    private final boolean ok;
    private final String error;
    private Map<String, Object> values;

    public TaskResult(boolean ok, String error) {
        this.ok = ok;
        this.error = error;
    }

    public TaskResult(boolean ok, String error, Map<String, Object> values) {
        this.ok = ok;
        this.error = error;

        // make sure the map is serializable and mutable
        this.values = new HashMap<>(values != null ? values : Collections.emptyMap());
    }

    public boolean ok() {
        return ok;
    }

    @Nullable
    public String error() {
        return error;
    }

    public TaskResult value(String key, Object value) {
        if (values == null) {
            values = new HashMap<>();
        }
        values.put(key, value);
        return this;
    }

    public TaskResult values(Map<String, Object> items) {
        if (values == null) {
            values = new HashMap<>();
        }
        values.putAll(items);
        return this;
    }

    public Map<String, Object> values() {
        if (values == null) {
            return Collections.emptyMap();
        }
        return values;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", ok);
        if (error != null) {
            result.put("error", error);
        }
        if (values != null) {
            result.putAll(values);
        }
        return result;
    }
}
