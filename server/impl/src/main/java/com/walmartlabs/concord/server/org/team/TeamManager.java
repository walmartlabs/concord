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


import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.team.TeamEntry;
import com.walmartlabs.concord.server.api.org.team.TeamRole;
import com.walmartlabs.concord.server.api.org.team.TeamUserEntry;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.api.user.UserType;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.UnauthorizedException;
import org.jooq.DSLContext;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.ws.rs.WebApplicationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
public class TeamManager {

    /**
     * Default organization's team ID
     */
    public static final UUID DEFAULT_ORG_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Name of the default team in each organization.
     */
    public static final String DEFAULT_TEAM_NAME = "default";

    private final TeamDao teamDao;
    private final OrganizationDao orgDao;
    private final OrganizationManager orgManager;
    private final UserManager userManager;
    private final LdapManager ldapManager;
    private final AuditLog auditLog;

    @Inject
    public TeamManager(TeamDao teamDao,
                       OrganizationDao orgDao,
                       OrganizationManager orgManager,
                       UserManager userManager,
                       LdapManager ldapManager,
                       AuditLog auditLog) {

        this.teamDao = teamDao;
        this.orgDao = orgDao;
        this.orgManager = orgManager;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
        this.auditLog = auditLog;
    }

    public UUID insert(UUID orgId, String teamName, String description) {
        assertAccess(orgId, TeamRole.OWNER);

        UUID teamId = teamDao.txResult(tx -> {
            UUID tId = teamDao.insert(tx, orgId, teamName, description);

            // add the current user as a team maintainer
            UUID userId = UserPrincipal.assertCurrent().getId();
            teamDao.upsertUser(tx, tId, userId, TeamRole.MAINTAINER);

            return tId;
        });

        auditLog.add(AuditObject.TEAM, AuditAction.CREATE)
                .field("id", teamId)
                .field("orgId", orgId)
                .field("name", teamName)
                .log();

        return teamId;
    }

    public void update(UUID teamId, String teamName, String description) {
        UUID orgId = teamDao.getOrgId(teamId);
        assertAccess(orgId, teamId, null, TeamRole.MAINTAINER, true);

        teamDao.update(teamId, teamName, description);

        // TODO delta?
        auditLog.add(AuditObject.TEAM, AuditAction.UPDATE)
                .field("id", teamId)
                .log();
    }

    public void delete(String orgName, String teamName) {
        TeamEntry t = assertTeam(orgName, teamName, TeamRole.OWNER, true, true);

        teamDao.delete(t.getId());

        auditLog.add(AuditObject.TEAM, AuditAction.DELETE)
                .field("id", t.getId())
                .field("orgId", t.getOrgId())
                .field("name", t.getName())
                .log();
    }

    public void addUsers(String orgName, String teamName, boolean replace, Collection<TeamUserEntry> users) {
        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MAINTAINER, true, true);

        Map<String, UUID> userIds = new HashMap<>();
        for (TeamUserEntry u : users) {
            UserType type = u.getUserType();
            if (type == null) {
                type = UserPrincipal.assertCurrent().getType();
            }

            UUID id = getOrCreateUserId(u.getUsername(), type);
            userIds.put(u.getUsername(), id);
        }

        teamDao.tx(tx -> {
            if (replace) {
                teamDao.removeUsers(tx, t.getId());
            }

            for (TeamUserEntry u : users) {
                TeamRole role = u.getRole();
                if (role == null) {
                    role = TeamRole.MEMBER;
                }

                UUID id = userIds.get(u.getUsername());
                teamDao.upsertUser(tx, t.getId(), id, role);
            }

            validateUsers(tx, t.getOrgId());
        });

        auditLog.add(AuditObject.TEAM, AuditAction.UPDATE)
                .field("id", t.getId())
                .field("orgId", t.getOrgId())
                .field("name", t.getName())
                .field("action", "addUsers")
                .field("users", users)
                .log();
    }

    private void validateUsers(DSLContext tx, UUID orgId) {
        if(!orgDao.hasRole(tx, orgId, TeamRole.OWNER)) {
            throw new ValidationErrorsException("Organization must have at least one OWNER");
        }
    }

    public void removeUsers(String orgName, String teamName, Collection<String> usernames) {
        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MAINTAINER, true, true);

        Collection<UUID> userIds = usernames.stream()
                .map(userManager::getId)
                .flatMap(id -> id.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toSet());

        teamDao.removeUsers(t.getId(), userIds);

        auditLog.add(AuditObject.TEAM, AuditAction.UPDATE)
                .field("id", t.getId())
                .field("orgId", t.getOrgId())
                .field("name", t.getName())
                .field("action", "removeUsers")
                .field("users", usernames)
                .log();
    }

    public TeamEntry assertExisting(UUID orgId, UUID teamId, String teamName) {
        if (teamId != null) {
            TeamEntry e = teamDao.get(teamId);
            if (e == null) {
                throw new ValidationErrorsException("Team not found: " + teamId);
            }
            return e;
        }

        if (teamName != null) {
            TeamEntry e = teamDao.getByName(orgId, teamName);
            if (e == null) {
                throw new ValidationErrorsException("Team not found: " + teamName);
            }
            return e;
        }

        throw new ValidationErrorsException("Team ID or name is required");
    }

    public void assertAccess(UUID orgId, TeamRole requiredRole) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin()) {
            return;
        }

        if (!teamDao.isInAnyTeam(orgId, p.getId(), TeamRole.atLeast(requiredRole))) {
            throw new UnauthorizedException("The current user (" + p.getUsername() + ") does not have the required role: " + requiredRole);
        }
    }

    public TeamEntry assertAccess(UUID orgId, String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        return assertAccess(orgId, null, teamName, requiredRole, teamMembersOnly);
    }

    public TeamEntry assertAccess(UUID orgId, UUID teamId, String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        TeamEntry e = assertExisting(orgId, teamId, teamName);

        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin()) {
            return e;
        }

        if (requiredRole != null && teamMembersOnly) {
            if (!teamDao.hasUser(e.getId(), p.getId(), TeamRole.atLeast(requiredRole))) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") does not have the required role: " + requiredRole);
            }
        }

        return e;
    }

    private TeamEntry assertTeam(String orgName, String teamName, TeamRole requiredRole,
                                 boolean orgMembersOnly, boolean teamMembersOnly) {

        OrganizationEntry org = orgManager.assertAccess(orgName, orgMembersOnly);
        return assertAccess(org.getId(), teamName, requiredRole, teamMembersOnly);
    }

    private UUID getOrCreateUserId(String username, UserType type) {
        UserEntry user = userManager.getOrCreate(username, type);

        if (user == null) {
            // TODO check the type
            try {
                // TODO this should be abstracted away
                LdapPrincipal p = ldapManager.getPrincipal(username);
                if (p == null) {
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
