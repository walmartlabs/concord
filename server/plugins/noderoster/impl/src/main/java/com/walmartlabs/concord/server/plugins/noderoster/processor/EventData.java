package com.walmartlabs.concord.server.plugins.noderoster.processor;

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

import java.util.Map;

public class EventData {

    private final Map<String, Object> data;

    public String getAction() {
        return getString("action");
    }

    public String getTask() {
        return getString("task");
    }

    public boolean isOk() {
        return "ok".equalsIgnoreCase(getString("status"));
    }

    public boolean isPostEvent() {
        return "post".equals(getString("phase"));
    }

    public String getHost() {
        return getString("host");
    }

    public String getString(String param) {
        Object o = data.get(param);
        if (o instanceof String) {
            return (String) data.get(param);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String param) {
        Object o = data.get(param);
        if (o instanceof Map) {
            return (Map<String, Object>) data.get(param);
        }
        return null;
    }

    public EventData(Map<String, Object> data) {
        this.data = data;
    }
}
