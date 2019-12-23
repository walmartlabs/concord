package com.walmartlabs.concord.server.sdk.events;

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

import com.walmartlabs.concord.server.sdk.ProcessKey;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

public class ProcessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ProcessKey processKey;
    private final String eventType;
    private final OffsetDateTime eventDate;

    public ProcessEvent(ProcessKey processKey, String eventType, OffsetDateTime eventDate, Map<String, Object> data) {
        this.processKey = processKey;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.data = data;
    }

    private final Map<String, Object> data;

    public ProcessKey getProcessKey() {
        return processKey;
    }

    public String getEventType() {
        return eventType;
    }

    public OffsetDateTime getEventDate() {
        return eventDate;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
