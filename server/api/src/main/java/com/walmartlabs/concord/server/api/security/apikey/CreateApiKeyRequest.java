package com.walmartlabs.concord.server.api.security.apikey;

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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.user.UserType;

import java.io.Serializable;
import java.util.UUID;

public class CreateApiKeyRequest implements Serializable {

    private final UUID userId;
    private final String username;
    private final UserType userType;

    public CreateApiKeyRequest(String username) {
        this(null, username, null);
    }

    public CreateApiKeyRequest(String username, UserType userType) {
        this(null, username, userType);
    }

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("userId") UUID userId,
                               @JsonProperty("username") String username,
                               @JsonProperty("userType") UserType userType) {
        this.userId = userId;
        this.username = username;
        this.userType = userType;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public UserType getUserType() {
        return userType;
    }

    @Override
    public String toString() {
        return "CreateApiKeyRequest{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", userType=" + userType +
                '}';
    }
}
