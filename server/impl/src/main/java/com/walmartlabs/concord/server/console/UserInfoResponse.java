package com.walmartlabs.concord.server.console;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.org.team.TeamRole;
import org.immutables.value.Value;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
@JsonInclude(Include.NON_EMPTY)
@JsonSerialize(as = ImmutableUserInfoResponse.class)
@JsonDeserialize(as = ImmutableUserInfoResponse.class)
public interface UserInfoResponse extends Serializable {

    @Serial
    long serialVersionUID = 1L;

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableUserTeamInfo.class)
    @JsonDeserialize(as = ImmutableUserTeamInfo.class)
    interface UserTeamInfo {

        @Value.Parameter
        String orgName();

        @Value.Parameter
        String teamName();

        @Value.Parameter
        TeamRole role();

        static UserTeamInfo of(String orgName, String teamName, TeamRole role) {
            return ImmutableUserTeamInfo.of(orgName, teamName, role);
        }
    }

    UUID id();

    String displayName();

    List<UserTeamInfo> teams();

    List<String> roles();

    Set<String> ldapGroups();

    static ImmutableUserInfoResponse.Builder builder() {
        return ImmutableUserInfoResponse.builder();
    }
}
