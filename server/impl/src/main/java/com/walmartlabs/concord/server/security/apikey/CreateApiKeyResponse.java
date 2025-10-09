package com.walmartlabs.concord.server.security.apikey;

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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

public class CreateApiKeyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean ok = true;
    private final UUID id;
    private final String key;

    @JsonCreator
    public CreateApiKeyResponse(@JsonProperty("id") UUID id, @JsonProperty("key") String key) {
        this.id = id;
        this.key = key;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateApiKeyResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", key='" + key + '\'' +
                '}';
    }
}
