package com.walmartlabs.concord.server.org.team;

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
import com.walmartlabs.concord.server.user.UserType;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class TeamUserEntry implements Serializable {

    private final UUID userId;
    private final String username;
    private final UserType userType;
    private final TeamRole role;

    public TeamUserEntry(String username, TeamRole role) {
        this(null, username, null, role);
    }

    @JsonCreator
    public TeamUserEntry(@JsonProperty("userId") UUID userId,
                         @JsonProperty("username") String username,
                         @JsonProperty("userType") UserType userType,
                         @JsonProperty("role") TeamRole role) {

        this.userId = userId;
        this.username = username;
        this.userType = userType;
        this.role = role;
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

    public TeamRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "TeamUserEntry{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", userType=" + userType +
                ", role=" + role +
                '}';
    }
}
