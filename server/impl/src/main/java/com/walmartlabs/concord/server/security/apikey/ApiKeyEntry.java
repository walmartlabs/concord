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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ApiKeyEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    private final UUID userId;

    @ConcordKey
    private final String name;

    private final OffsetDateTime expiredAt;

    @JsonCreator
    public ApiKeyEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("userId") UUID userId,
                       @JsonProperty("name") String name,
                       @JsonProperty("expiredAt") OffsetDateTime expiredAt) {

        this.id = id;
        this.userId = userId;
        this.name = name;
        this.expiredAt = expiredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public OffsetDateTime getExpiredAt() {
        return expiredAt;
    }

    @Override
    public final String toString() {
        return "ApiKeyEntry{" +
                "id=" + id +
                ", userId=" + userId +
                ", name='" + name + '\'' +
                ", expiredAt=" + expiredAt +
                '}';
    }
}
