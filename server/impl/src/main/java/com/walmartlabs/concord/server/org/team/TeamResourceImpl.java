package com.walmartlabs.concord.server.org.team;

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

import com.walmartlabs.concord.server.api.GenericOperationResult;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.team.*;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Named
public class TeamResourceImpl implements TeamResource, Resource {

    private final TeamDao teamDao;
    private final TeamManager teamManager;
    private final OrganizationManager orgManager;
    private final UserManager userManager;
    private final LdapManager ldapManager;

    @Inject
    public TeamResourceImpl(TeamDao teamDao,
                            TeamManager teamManager,
                            OrganizationManager orgManager,
                            UserManager userManager,
                            LdapManager ldapManager) {

        this.teamDao = teamDao;
        this.teamManager = teamManager;
        this.orgManager = orgManager;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
    }

    @Override
    @Validate
    public CreateTeamResponse createOrUpdate(String orgName, TeamEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        UUID teamId = entry.getId();
        if (teamId == null) {
            teamId = teamDao.getId(org.getId(), entry.getName());
        }
        if (teamId != null) {
            teamManager.update(teamId, entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.UPDATED, teamId);
        } else {
            teamId = teamManager.insert(org.getId(), entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.CREATED, teamId);
        }
    }

    @Override
    public TeamEntry get(String orgName, String teamName) {
        return assertTeam(orgName, teamName, null, true, false);
    }

    @Override
    public GenericOperationResult delete(String orgName, String teamName) {
        teamManager.delete(orgName, teamName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @Override
    public List<TeamEntry> list(String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return teamDao.list(org.getId());
    }

    @Override
    public List<TeamUserEntry> listUsers(String orgName, String teamName) {
        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listUsers(t.getId());
    }

    @Override
    public AddTeamUsersResponse addUsers(String orgName, String teamName, Collection<TeamUserEntry> users) {
        if (users == null || users.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        teamManager.addUsers(orgName, teamName, users);
        return new AddTeamUsersResponse();
    }

    @Override
    public RemoveTeamUsersResponse removeUsers(String orgName, String teamName, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        teamManager.removeUsers(orgName, teamName, usernames);
        return new RemoveTeamUsersResponse();
    }

    private TeamEntry assertTeam(String orgName, String teamName, TeamRole requiredRole,
                                 boolean orgMembersOnly, boolean teamMembersOnly) {

        OrganizationEntry org = orgManager.assertAccess(orgName, orgMembersOnly);
        return teamManager.assertAccess(org.getId(), teamName, requiredRole, teamMembersOnly);
    }
}
