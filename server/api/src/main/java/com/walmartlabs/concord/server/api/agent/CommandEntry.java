package com.walmartlabs.concord.server.api.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.api.CommandType;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandEntry {

    private final CommandType type;

    private final Map<String, Object> payload;

    @JsonCreator
    public CommandEntry(
            @JsonProperty("type") CommandType type,
            @JsonProperty("payload") Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
    }

    public CommandType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "CommandEntity{" +
                "type=" + type +
                ", payload=" + payload +
                '}';
    }
}
