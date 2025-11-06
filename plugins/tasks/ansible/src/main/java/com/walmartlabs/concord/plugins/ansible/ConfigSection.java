package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

public class ConfigSection {

    private final Map<String, Object> items;

    public ConfigSection(Map<String, Object> items) {
        this.items = items;
    }

    public ConfigSection prependPath(String key, String path) {
        String current = getString(key);
        if (current != null) {
            items.put(key, path + ":" + current);
        } else {
            items.put(key, path);
        }
        return this;
    }

    public ConfigSection put(String key, String value) {
        items.put(key, value);
        return this;
    }

    public String getString(String key) {
        Object v = items.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s;
        }
        throw new RuntimeException("Expected a string value in '%s', got %s (%s)".formatted(key, v, v.getClass()));
    }
}
