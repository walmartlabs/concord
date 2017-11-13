package com.walmartlabs.concord.server.landing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.landing.CreateLandingResponse;
import com.walmartlabs.concord.server.api.landing.DeleteLandingResponse;
import com.walmartlabs.concord.server.api.landing.LandingEntry;
import com.walmartlabs.concord.server.api.landing.LandingPageResource;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.ProjectManager;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.team.TeamManager;
import org.jooq.Configuration;
import org.postgresql.util.Base64;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.project.RepositoryUtils.assertRepository;

@Named
public class LandingPageResourceImpl extends AbstractDao implements LandingPageResource, Resource {

    private static final String LP_META_FILE_NAME = "landing.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TeamManager teamManager;
    private final LandingDao landingDao;
    private final ProjectDao projectDao;
    private final ProjectManager projectManager;
    private final RepositoryManager repositoryManager;

    @Inject
    public LandingPageResourceImpl(Configuration cfg,
                                   TeamManager teamManager, LandingDao landingDao, ProjectDao projectDao,
                                   ProjectManager projectManager, RepositoryManager repositoryManager) {
        super(cfg);

        this.teamManager = teamManager;
        this.landingDao = landingDao;
        this.projectDao = projectDao;
        this.projectManager = projectManager;
        this.repositoryManager = repositoryManager;
    }

    @Override
    public CreateLandingResponse createOrUpdate(LandingEntry entry) {
        TeamEntry team = teamManager.assertTeam(entry.getTeamId(), entry.getTeamName());
        ProjectEntry project = assertProject(team.getId(), entry.getProjectName(), TeamRole.WRITER, true);
        RepositoryEntry repository = assertRepository(project, entry.getRepositoryName());

        byte[] icon = null;
        if (entry.getIcon() != null) {
            icon = Base64.decode(entry.getIcon());
        }

        if (entry.getId() != null) {
            landingDao.update(entry.getId(), project.getId(), repository.getId(), entry.getName(), entry.getDescription(), icon);
            return new CreateLandingResponse(OperationResult.UPDATED, entry.getId());
        } else {
            UUID landingId = landingDao.insert(project.getId(), repository.getId(), entry.getName(), entry.getDescription(), icon);
            return new CreateLandingResponse(OperationResult.CREATED, landingId);
        }
    }

    @Override
    public DeleteLandingResponse delete(UUID id) {
        LandingEntry e = landingDao.get(id);
        if (e == null) {
            throw new ValidationErrorsException("A valid landing page id is required");
        }

        UUID teamId = projectDao.getTeamId(e.getProjectId());
        assertProject(teamId, e.getProjectName(), TeamRole.WRITER, true);

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

    @Override
    public Response refresh(String projectName, String repositoryName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);

        Path lpMetaFile = repositoryManager.fetch(p.getId(), r)
                .resolve(LP_META_FILE_NAME);

        LandingEntry le = loadEntry(lpMetaFile);

        tx(tx -> {
            landingDao.delete(tx, p.getId(), r.getId());
            if (le != null) {
                byte[] icon = le.getIcon() != null ? Base64.decode(le.getIcon()) : null;
                landingDao.insert(tx, p.getId(), r.getId(), le.getName(), le.getDescription(), icon);
            }
        });

        return Response.ok().build();
    }

    private LandingEntry loadEntry(Path file) {
        if (!Files.exists(file)) {
            return null;
        }

        try {
            return objectMapper.readValue(file.toFile(), LandingEntry.class);
        } catch (IOException e) {
            // ignore
            return null;
        }
    }

    private ProjectEntry assertProject(UUID teamId, String projectName, TeamRole role, boolean membersOnly) {
        if (projectName == null) {
            throw new ValidationErrorsException("A valid project name is required");
        }

        UUID id = projectDao.getId(teamId, projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }


        return projectManager.assertProjectAccess(id, role, membersOnly);
    }
}
