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

    public TeamEntry assertExisting(UUID orgId, UUID id, String name) {
        if (id != null) {
            TeamEntry e = teamDao.get(id);
            if (e == null) {
                throw new ValidationErrorsException("Team not found: " + id);
            }
            return e;
        }

        if (name != null) {
            TeamEntry e = teamDao.getByName(orgId, name);
            if (e == null) {
                throw new ValidationErrorsException("Team not found: " + name);
            }
            return e;
        }

        throw new ValidationErrorsException("Team ID or name is required");
    }

    public TeamEntry assertAccess(UUID orgId, String name, TeamRole requiredRole, boolean teamMembersOnly) {
        return assertAccess(orgId, null, name, requiredRole, teamMembersOnly);
    }

    public TeamEntry assertAccess(UUID orgId, UUID id, String name, TeamRole requiredRole, boolean teamMembersOnly) {
        TeamEntry e = assertExisting(orgId, id, name);

        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin()) {
            return e;
        }

        if (requiredRole != null && teamMembersOnly) {
            if (!teamDao.hasUser(e.getId(), p.getId(), TeamRole.atLeast(requiredRole))) {
                throw new UnauthorizedException("The current user doesn't belong to the team: " + e.getName());
            }
        }

        return e;
    }
}
