package com.walmartlabs.concord.server.api.user;

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
import com.walmartlabs.concord.common.validation.ConcordUsername;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class CreateUserRequest implements Serializable {

    @NotNull
    @ConcordUsername
    private final String username;

    private final Boolean admin;

    private final UserType type;

    public CreateUserRequest(String username, UserType type) {
        this(username, type, false);
    }

    @JsonCreator
    public CreateUserRequest(@JsonProperty("username") String username,
                             @JsonProperty("userType") UserType type,
                             @JsonProperty("admin") Boolean admin) {

        this.username = username;
        this.type = type;
        this.admin = admin;
    }

    public String getUsername() {
        return username;
    }

    public UserType getType() {
        return type;
    }

    public Boolean getAdmin() {
        return admin;
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "username='" + username + '\'' +
                ", admin=" + admin +
                ", type=" + type +
                '}';
    }
}
