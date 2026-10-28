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

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;

@Path("/api/v2/org/{orgName}/team/{teamName}/groups/{sourceId}")
public class TeamGroupsResource implements Resource {
    private final TeamDao dao;
    private final TeamManager manager;
    private final OrganizationManager organizations;
    private final AuthorizationService authorization;

    @Inject
    public TeamGroupsResource(TeamDao dao, TeamManager manager, OrganizationManager organizations, AuthorizationService authorization) {
        this.dao = dao;
        this.manager = manager;
        this.organizations = organizations;
        this.authorization = authorization;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTeamSourceGroups")
    public List<TeamSourceGroupEntry> list(@PathParam("orgName") String orgName, @PathParam("teamName") String teamName,
                                           @PathParam("sourceId") String sourceId) {
        OrganizationEntry org = organizations.assertAccess(orgName, true);
        TeamEntry team = manager.assertAccess(org.getId(), teamName, TeamRole.MEMBER, false);
        validateSource(sourceId);
        return dao.listGroups(team.getId(), sourceId);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "putTeamSourceGroups")
    public AddTeamLdapGroupsResponse put(@PathParam("orgName") String orgName, @PathParam("teamName") String teamName,
                                         @PathParam("sourceId") String sourceId,
                                         @QueryParam("replace") @DefaultValue("false") boolean replace,
                                         Collection<TeamSourceGroupEntry> groups) {
        if (groups == null || (!replace && groups.isEmpty()) || groups.stream().anyMatch(g -> g == null || g.groupId() == null || g.groupId().isBlank() || g.role() == null)) {
            throw new ValidationErrorsException("Invalid group list");
        }
        validateSource(sourceId);
        manager.addGroups(orgName, teamName, sourceId, replace, groups);
        return new AddTeamLdapGroupsResponse();
    }

    private void validateSource(String sourceId) {
        if (!authorization.isActiveSource(sourceId)) throw new ValidationErrorsException("Unknown or inactive authorization source: " + sourceId);
    }
}
