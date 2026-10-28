package com.walmartlabs.concord.server.org.team;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.UUID;

@Path("/api/v2/org/{orgName}/team/{teamName}/users")
@Tag(name = "Teams")
public class TeamUsersV2Resource implements Resource {
    private final TeamManager manager;

    @Inject
    public TeamUsersV2Resource(TeamManager manager) {
        this.manager = manager;
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Remove users from a team by ID", operationId = "removeUsersFromTeamById")
    public RemoveTeamUsersResponse removeUsers(@PathParam("orgName") @ConcordKey String orgName,
                                               @PathParam("teamName") @ConcordKey String teamName,
                                               Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty() || userIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ValidationErrorsException("Empty or invalid user list");
        }
        manager.removeUsersById(orgName, teamName, userIds);
        return new RemoveTeamUsersResponse();
    }
}
