package com.walmartlabs.concord.server.api.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessEventEntry implements Serializable {

    private final UUID id;
    private final ProcessEventType eventType;
    private final Date eventDate;
    private final Object data;

    @JsonCreator
    public ProcessEventEntry(@JsonProperty("id") UUID id,
                             @JsonProperty("eventType") ProcessEventType eventType,
                             @JsonProperty("eventDate") Date eventDate,
                             @JsonProperty("data") String data) {

        this.id = id;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.data = data;
    }

    public UUID getId() {
        return id;
    }

    public ProcessEventType getEventType() {
        return eventType;
    }

    public Date getEventDate() {
        return eventDate;
    }

    @JsonRawValue
    public Object getData() {
        return data;
    }
}
