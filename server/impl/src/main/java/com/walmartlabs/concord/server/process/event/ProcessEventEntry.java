package com.walmartlabs.concord.server.process.event;

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

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessEventEntry implements Serializable {

    private final UUID id;
    private final String eventType;
    private final Object data;

    /**
     * should match the format in {@link com.walmartlabs.concord.server.IsoDateParam}
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final Date eventDate;

    @JsonCreator
    public ProcessEventEntry(@JsonProperty("id") UUID id,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("eventDate") Date eventDate,
                             @JsonProperty("data") Object data) {

        this.id = id;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.data = data;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
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
