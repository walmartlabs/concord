package com.walmartlabs.concord.server.org;

import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.team.TeamRole;
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

    @Inject
    public OrganizationManager(OrganizationDao orgDao, TeamDao teamDao, UserManager userManager) {
        this.orgDao = orgDao;
        this.teamDao = teamDao;
        this.userManager = userManager;
    }

    public UUID create(OrganizationEntry entry) {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to create new organizations");
        }

        return orgDao.txResult(tx -> {
            UUID orgId = orgDao.insert(entry.getName());

            // ...add the current user to the default new as an OWNER
            UUID teamId = teamDao.insert(tx, orgId, TeamManager.DEFAULT_TEAM_NAME, "Default team");
            teamDao.addUsers(tx, teamId, p.getId(), TeamRole.OWNER);

            return orgId;
        });
    }

    public void update(OrganizationEntry entry) {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to update organizations");
        }

        UUID orgId = entry.getId();
        orgDao.update(orgId, entry.getName());
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

    public OrganizationEntry assertAccess(UUID orgId, String name, boolean orgMembersOnly) {
        OrganizationEntry e = assertExisting(orgId, name);

        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin()) {
            // an admin can access any organization
            return e;
        }

        if (orgMembersOnly) {
            if (!userManager.isInOrganization(e.getId())) {
                throw new UnauthorizedException("The current user doesn't belong to the specified organization: " + e.getName());
            }
        }

        return e;
    }
}
