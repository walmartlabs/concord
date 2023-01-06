package com.walmartlabs.concord.server.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.server.org.team.TeamRole;
import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
public interface UserTeam {

    UUID teamId();

    TeamRole role();

    static UserTeam of(UUID teamId, TeamRole role) {
        return UserTeam.builder()
                .teamId(teamId)
                .role(role)
                .build();
    }

    static ImmutableUserTeam.Builder builder() {
        return ImmutableUserTeam.builder();
    }
}
