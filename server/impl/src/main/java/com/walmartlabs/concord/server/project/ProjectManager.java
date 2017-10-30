package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectVisibility;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.team.TeamManager;
import org.jooq.Field;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

@Named
public class ProjectManager {

    private final ProjectDao projectDao;
    private final TeamManager teamManager;

    @Inject
    public ProjectManager(ProjectDao projectDao, TeamManager teamManager) {
        this.projectDao = projectDao;
        this.teamManager = teamManager;
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        UserPrincipal p = UserPrincipal.getCurrent();

        UUID userId = p.getId();
        if (p.isAdmin()) {
            // admins can see any project, so we shouldn't filter projects by user
            userId = null;
        }

        return projectDao.list(userId, sortField, asc);
    }

    public ProjectEntry assertProjectAccess(UUID projectId, TeamRole requiredRole, boolean teamMembersOnly) {
        ProjectEntry p = projectDao.get(projectId);
        if (p == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        boolean admin = UserPrincipal.getCurrent().isAdmin();
        if (!admin && (teamMembersOnly || p.getVisibility() != ProjectVisibility.PUBLIC)) {
            teamManager.assertTeamAccess(p.getTeamId(), requiredRole, teamMembersOnly);
        }

        return p;
    }
}
