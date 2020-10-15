package com.walmartlabs.concord.project.yaml.model;

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

import com.fasterxml.jackson.core.JsonLocation;

import java.io.Serializable;
import java.util.Map;

public class YamlTrigger implements Serializable {

    private static final long serialVersionUID = 1L;

    private final JsonLocation location;
    private final String eventSource;
    private final Map<String, Object> options;

    public YamlTrigger(JsonLocation location, String eventSource, Map<String, Object> options) {
        this.location = location;
        this.eventSource = eventSource;
        this.options = options;
    }

    public String getEventSource() {
        return eventSource;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public JsonLocation getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "YamlTrigger{" +
                "location=" + location +
                ", eventSource='" + eventSource + '\'' +
                ", options=" + options +
                '}';
    }
}
