package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ProcessStatusHistoryEntry implements Serializable {

    private final UUID id;
    private final ProcessStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final Timestamp changeDate;

    @JsonCreator
    public ProcessStatusHistoryEntry(@JsonProperty("id") UUID id,
                                     @JsonProperty("status") ProcessStatus status,
                                     @JsonProperty("changeDate") Timestamp changeDate) {

        this.id = id;
        this.status = status;
        this.changeDate = changeDate;
    }

    public UUID getId() {
        return id;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public Timestamp getChangeDate() {
        return changeDate;
    }

    @Override
    public String toString() {
        return "ProcessStatusHistoryEntry{" +
                "id=" + id +
                ", status='" + status + '\'' +
                ", changeDate=" + changeDate +
                '}';
    }
}
