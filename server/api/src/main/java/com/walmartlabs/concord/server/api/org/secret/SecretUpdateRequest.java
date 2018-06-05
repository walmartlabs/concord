package com.walmartlabs.concord.server.api.org.secret;

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
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecretUpdateRequest implements Serializable {

    @ConcordKey
    private final String name;

    private final SecretVisibility visibility;

    public SecretUpdateRequest(SecretVisibility visibility) {
        this(null, visibility);
    }

    @JsonCreator
    public SecretUpdateRequest(@JsonProperty("name") String name,
                               @JsonProperty("visibility") SecretVisibility visibility) {

        this.name = name;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public SecretVisibility getVisibility() {
        return visibility;
    }

    @Override
    public String toString() {
        return "SecretUpdateRequest{" +
                "name='" + name + '\'' +
                ", visibility=" + visibility +
                '}';
    }
}
