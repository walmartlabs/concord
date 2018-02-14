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

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.team.*;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.api.user.UserType;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.ws.rs.WebApplicationException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        UUID teamId = teamDao.getId(org.getId(), entry.getName());
        if (teamId != null) {
            teamManager.assertAccess(org.getId(), teamId, null, TeamRole.MAINTAINER, true);
            teamDao.update(teamId, entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.UPDATED, teamId);
        } else {
            teamManager.assertAccess(org.getId(), TeamRole.OWNER);
            teamId = teamDao.txResult(tx -> {
                UUID tId = teamDao.insert(tx, org.getId(), entry.getName(), entry.getDescription());

                // add the current user as a team maintainer
                UUID userId = UserPrincipal.getCurrent().getId();
                teamDao.addUser(tx, tId, userId, TeamRole.MAINTAINER);

                return tId;
            });
            return new CreateTeamResponse(OperationResult.CREATED, teamId);
        }
    }

    @Override
    @RequiresAuthentication
    public TeamEntry get(String orgName, String teamName) {
        return assertTeam(orgName, teamName, null, true, false);
    }

    @Override
    @RequiresAuthentication
    public List<TeamEntry> list(String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return teamDao.list(org.getId());
    }

    @Override
    @RequiresAuthentication
    public List<TeamUserEntry> listUsers(String orgName, String teamName) {
        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listUsers(t.getId());
    }

    @Override
    public AddTeamUsersResponse addUsers(String orgName, String teamName, Collection<TeamUserEntry> users) {
        if (users == null || users.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MAINTAINER, true, true);

        teamDao.tx(tx -> {
            for (TeamUserEntry u : users) {
                UserType type = u.getUserType();
                if (type == null) {
                    type = UserPrincipal.getCurrent().getType();
                }

                UUID userId = getOrCreateUserId(u.getUsername(), type);

                TeamRole role = u.getRole();
                if (role == null) {
                    role = TeamRole.MEMBER;
                }

                teamDao.addUser(tx, t.getId(), userId, role);
            }
        });

        return new AddTeamUsersResponse();
    }

    @Override
    public RemoveTeamUsersResponse removeUsers(String orgName, String teamName, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MAINTAINER, true, true);

        Collection<UUID> userIds = usernames.stream()
                .map(userManager::getId)
                .flatMap(id -> id.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toSet());

        teamDao.removeUsers(t.getId(), userIds);

        return new RemoveTeamUsersResponse();
    }

    private TeamEntry assertTeam(String orgName, String teamName, TeamRole requiredRole,
                                 boolean orgMembersOnly, boolean teamMembersOnly) {

        OrganizationEntry org = orgManager.assertAccess(orgName, orgMembersOnly);
        return teamManager.assertAccess(org.getId(), teamName, requiredRole, teamMembersOnly);
    }

    private void assertIsAdmin() {
        if (!UserPrincipal.getCurrent().isAdmin()) {
            throw new UnauthorizedException("The current user is not an administrator");
        }
    }

    private UUID getOrCreateUserId(String username, UserType type) {
        UserEntry user = userManager.getOrCreate(username, type);

        if (user == null) {
            try {
                LdapInfo i = ldapManager.getInfo(username);
                if (i == null) {
                    throw new WebApplicationException("User not found: " + username);
                }
            } catch (NamingException e) {
                throw new WebApplicationException("Error while retrieving LDAP data: " + e.getMessage(), e);
            }

            user = userManager.getOrCreate(username, type);
        }

        return user.getId();
    }
}
