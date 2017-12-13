package com.walmartlabs.concord.server.org.team;

import com.walmartlabs.concord.server.api.org.team.TeamEntry;
import com.walmartlabs.concord.server.api.org.team.TeamRole;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class TeamManager {

    public static final UUID DEFAULT_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String DEFAULT_TEAM_NAME = "default";

    private final TeamDao teamDao;

    @Inject
    public TeamManager(TeamDao teamDao) {
        this.teamDao = teamDao;
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
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin()) {
            return;
        }

        if (!teamDao.isInAnyTeam(orgId, p.getId(), TeamRole.atLeast(requiredRole))) {
            throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't belong to any team in the organization");
        }
    }

    public TeamEntry assertAccess(UUID orgId, String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        return assertAccess(orgId, null, teamName, requiredRole, teamMembersOnly);
    }

    public TeamEntry assertAccess(UUID orgId, UUID teamId, String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        TeamEntry e = assertExisting(orgId, teamId, teamName);

        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin()) {
            return e;
        }

        if (requiredRole != null && teamMembersOnly) {
            if (!teamDao.hasUser(e.getId(), p.getId(), TeamRole.atLeast(requiredRole))) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't belong to the team: " + e.getName());
            }
        }

        return e;
    }
}
