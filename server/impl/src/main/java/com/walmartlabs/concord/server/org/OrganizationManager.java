package com.walmartlabs.concord.server.org;

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

import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class OrganizationManager {

    // as defined in com/walmartlabs/concord/server/db/0.48.0.xml
    public static final UUID DEFAULT_ORG_ID = UUID.fromString("0fac1b18-d179-11e7-b3e7-d7df4543ed4f");
    public static final String DEFAULT_ORG_NAME = "Default";

    private final OrganizationDao orgDao;
    private final TeamDao teamDao;
    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public OrganizationManager(OrganizationDao orgDao,
                               TeamDao teamDao,
                               UserManager userManager,
                               AuditLog auditLog) {

        this.orgDao = orgDao;
        this.teamDao = teamDao;
        this.userManager = userManager;
        this.auditLog = auditLog;
    }

    public UUID create(OrganizationEntry entry) {
        UserPrincipal p = assertAdmin();

        UUID id = orgDao.txResult(tx -> {
            UUID orgId = orgDao.insert(entry.getName(), entry.getVisibility(), entry.getMeta(), entry.getCfg());

            // ...add the current user to the default new as an OWNER
            UUID teamId = teamDao.insert(tx, orgId, TeamManager.DEFAULT_TEAM_NAME, "Default team");
            teamDao.upsertUser(tx, teamId, p.getId(), TeamRole.OWNER);

            return orgId;
        });

        auditLog.add(AuditObject.ORGANIZATION, AuditAction.CREATE)
                .field("id", id)
                .field("name", entry.getName())
                .field("meta", entry.getMeta())
                .log();

        return id;
    }

    public void update(OrganizationEntry entry) {
        assertAdmin();

        UUID orgId = entry.getId();
        orgDao.update(orgId, entry.getName(), entry.getVisibility(), entry.getMeta(), entry.getCfg());

        // TODO delta?
        auditLog.add(AuditObject.ORGANIZATION, AuditAction.UPDATE)
                .field("id", orgId)
                .field("name", entry.getName())
                .field("meta", entry.getMeta())
                .log();
    }

    public OrganizationEntry assertExisting(UUID orgId, String orgName) {
        if (orgId != null) {
            OrganizationEntry e = orgDao.get(orgId);
            if (e == null) {
                throw new ValidationErrorsException("Organization not found: " + orgId);
            }
            return e;
        }

        if (orgName != null) {
            OrganizationEntry e = orgDao.getByName(orgName);
            if (e == null) {
                throw new ValidationErrorsException("Organization not found: " + orgName);
            }
            return e;
        }

        throw new ValidationErrorsException("Organization ID or name is required");
    }

    public OrganizationEntry assertAccess(UUID orgId, boolean orgMembersOnly) {
        return assertAccess(orgId, null, orgMembersOnly);
    }

    public OrganizationEntry assertAccess(String orgName, boolean orgMembersOnly) {
        return assertAccess(null, orgName, orgMembersOnly);
    }

    @WithTimer
    public OrganizationEntry assertAccess(UUID orgId, String name, boolean orgMembersOnly) {
        OrganizationEntry e = assertExisting(orgId, name);

        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin()) {
            // an admin can access any organization
            return e;
        }

        if (p.isGlobalReader() || p.isGlobalWriter()) {
            return e;
        }

        if (orgMembersOnly) {
            if (!userManager.isInOrganization(e.getId())) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't belong to the specified organization: " + e.getName());
            }
        }

        return e;
    }

    private static UserPrincipal assertAdmin() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (!p.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to update organizations");
        }
        return p;
    }
}
