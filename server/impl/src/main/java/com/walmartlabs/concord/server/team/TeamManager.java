package com.walmartlabs.concord.server.team;

import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.api.team.TeamVisibility;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

@Named
public class TeamManager {

    public static final UUID DEFAULT_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final TeamDao teamDao;

    @Inject
    public TeamManager(TeamDao teamDao) {
        this.teamDao = teamDao;
    }

    public List<TeamEntry> list() {
        UserPrincipal p = UserPrincipal.getCurrent();

        UUID userId = p.getId();
        if (p.isAdmin()) {
            // admins can see any team, so we shouldn't filter teams by user
            userId = null;
        }

        return teamDao.list(userId);
    }

    public TeamEntry assertTeam(UUID teamId, String teamName) {
        TeamEntry t = null;

        if (teamId != null) {
            t = teamDao.get(teamId);
            if (t == null) {
                throw new ValidationErrorsException("Team not found: " + teamId);
            }
        }

        if (teamId == null && teamName != null) {
            t = teamDao.getByName(teamName);
            if (t == null) {
                throw new ValidationErrorsException("Team not found: " + teamName);
            }
        }

        if (t == null) {
            t = teamDao.get(DEFAULT_TEAM_ID);
        }

        return t;
    }

    public TeamEntry assertTeamAccess(String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        return assertTeamAccess(null, teamName, requiredRole, teamMembersOnly);
    }

    public TeamEntry assertTeamAccess(UUID teamId, TeamRole requiredRole, boolean teamMembersOnly) {
        return assertTeamAccess(teamId, null, requiredRole, teamMembersOnly);
    }

    private TeamEntry assertTeamAccess(UUID teamId, String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        TeamEntry t = assertTeam(teamId, teamName);

        UserPrincipal p = UserPrincipal.getCurrent();
        boolean admin = p.isAdmin();
        if (!admin && (teamMembersOnly || t.getVisibility() != TeamVisibility.PUBLIC)) {
            if (!teamDao.hasUser(teamId, p.getId(), TeamRole.atLeast(requiredRole))) {
                throw new UnauthorizedException("The current user does not have access to the specified team (id=" + teamId + "). " +
                        "Required role: " + requiredRole);
            }
        }

        return t;
    }
}
