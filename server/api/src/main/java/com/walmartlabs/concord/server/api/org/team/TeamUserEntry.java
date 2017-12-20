package com.walmartlabs.concord.server.api.org.team;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class TeamUserEntry implements Serializable {

    private final UUID id;
    private final String username;
    private final TeamRole role;

    public TeamUserEntry(String username, TeamRole role) {
        this(null, username, role);
    }

    @JsonCreator
    public TeamUserEntry(@JsonProperty("id") UUID id,
                         @JsonProperty("username") String username,
                         @JsonProperty("role") TeamRole role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public TeamRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "TeamUserEntry{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}
