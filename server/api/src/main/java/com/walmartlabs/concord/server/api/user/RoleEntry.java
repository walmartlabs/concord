package com.walmartlabs.concord.server.api.user;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleEntry implements Serializable {

    private final UUID id;

    @ConcordKey
    private final String name;

    private final boolean globalReader;
    private final boolean globalWriter;

    @JsonCreator
    public RoleEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("globalReader") boolean globalReader,
                     @JsonProperty("globalWriter") boolean globalWriter) {

        this.id = id;
        this.name = name;
        this.globalReader = globalReader;
        this.globalWriter = globalWriter;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isGlobalReader() {
        return globalReader;
    }

    public boolean isGlobalWriter() {
        return globalWriter;
    }

    @Override
    public String toString() {
        return "RoleEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", globalReader=" + globalReader +
                ", globalWriter=" + globalWriter +
                '}';
    }
}
