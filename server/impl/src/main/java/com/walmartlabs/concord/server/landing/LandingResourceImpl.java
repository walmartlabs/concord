package com.walmartlabs.concord.server.landing;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.landing.CreateLandingResponse;
import com.walmartlabs.concord.server.api.landing.DeleteLandingResponse;
import com.walmartlabs.concord.server.api.landing.LandingEntry;
import com.walmartlabs.concord.server.api.landing.LandingResource;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.ProjectManager;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.postgresql.util.Base64;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

@Named
public class LandingResourceImpl implements LandingResource, Resource {

    private final LandingDao landingDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectManager projectManager;

    @Inject
    public LandingResourceImpl(LandingDao landingDao, ProjectDao projectDao, RepositoryDao repositoryDao,
                               ProjectManager projectManager) {
        this.landingDao = landingDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.projectManager = projectManager;
    }

    @Override
    public CreateLandingResponse createOrUpdate(LandingEntry entry) {
        UUID projectId = assertProject(entry.getProjectName());
        UUID repositoryId = assertRepository(projectId, entry.getRepositoryName());
        byte[] icon = null;
        if (entry.getIcon() != null) {
            icon = Base64.decode(entry.getIcon());
        }

        if (entry.getId() != null) {
            landingDao.update(entry.getId(), projectId, repositoryId, entry.getName(), entry.getDescription(), icon);
            return new CreateLandingResponse(OperationResult.UPDATED, entry.getId());
        } else {
            UUID landingId = landingDao.insert(projectId, repositoryId, entry.getName(), entry.getDescription(), icon);
            return new CreateLandingResponse(OperationResult.CREATED, landingId);
        }
    }

    @Override
    public DeleteLandingResponse delete(UUID id) {
        LandingEntry e = landingDao.get(id);
        if (e == null) {
            throw new ValidationErrorsException("A valid landing page id is required");
        }

        assertProject(e.getProjectName());

        landingDao.delete(id);
        return new DeleteLandingResponse();
    }

    @Override
    public List<LandingEntry> list() {
        UserPrincipal p = UserPrincipal.getCurrent();
        UUID userId = p.getId();

        if (p.isAdmin()) {
            // admins can see any LP
            userId = null;
        }

        return landingDao.list(userId);
    }

    private UUID assertRepository(UUID projectId, String repositoryName) {
        if (repositoryName == null) {
            throw new ValidationErrorsException("A valid repository name is required");
        }

        UUID id = repositoryDao.getId(projectId, repositoryName);
        if (id == null) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName + " in project" + projectId);
        }
        return id;
    }

    private UUID assertProject(String projectName) {
        if (projectName == null) {
            throw new ValidationErrorsException("A valid project name is required");
        }

        UUID id = projectDao.getId(projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }


        return projectManager.assertProjectAccess(id, TeamRole.WRITER, true).getId();
    }
}
