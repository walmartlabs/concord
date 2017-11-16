package com.walmartlabs.concord.server.triggers;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.api.trigger.TriggerEntry;
import com.walmartlabs.concord.server.api.trigger.TriggerResource;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.ProjectManager;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.team.TeamManager;
import org.apache.shiro.authz.UnauthorizedException;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.project.RepositoryUtils.assertRepository;

@Named
public class TriggerResourceImpl extends AbstractDao implements TriggerResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(TriggerResourceImpl.class);

    private final ProjectLoader projectLoader = new ProjectLoader();
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final TriggersDao triggersDao;
    private final RepositoryManager repositoryManager;
    private final ProjectManager projectManager;

    @Inject
    public TriggerResourceImpl(ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               TriggersDao triggersDao,
                               RepositoryManager repositoryManager,
                               ProjectManager projectManager,
                               Configuration cfg) {

        super(cfg);
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.triggersDao = triggersDao;
        this.repositoryManager = repositoryManager;
        this.projectManager = projectManager;
    }

    @Override
    public List<TriggerEntry> list(String projectName, String repositoryName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.READER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);

        return triggersDao.list(p.getId(), r.getId());
    }

    @Override
    public Response refreshAll() {
        assertAdmin();
        repositoryDao.list().parallelStream().forEach(r -> {
            try {
                refresh(r);
            } catch (Exception e) {
                log.warn("refreshAll -> {} refresh failed: {}", r.getId(), e.getMessage());
            }
        });
        return Response.ok().build();
    }

    @Override
    public Response refresh(String projectName, String repositoryName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);

        refresh(r);

        return Response.ok().build();
    }

    private void refresh(RepositoryEntry r) {
        Path repoPath = repositoryManager.fetch(r.getProjectId(), r);

        ProjectDefinition pd;
        try {
            pd = projectLoader.load(repoPath);
        } catch (IOException e) {
            log.error("refresh ['{}'] -> load project error", r.getId(), e);
            throw new WebApplicationException("Refresh failed", e);
        }

        tx(tx -> {
            triggersDao.delete(tx, r.getProjectId(), r.getId());
            pd.getTriggers().forEach(t -> triggersDao.insert(tx,
                    r.getProjectId(), r.getId(), t.getName(),
                    t.getEntryPoint(), t.getArguments(), t.getParams()));
        });

        log.info("refresh ['{}'] -> done, triggers count: {}", r.getId(), pd.getTriggers().size());
    }

    private ProjectEntry assertProject(UUID teamId, String projectName, TeamRole requiredRole, boolean teamMembersOnly) {
        if (projectName == null) {
            throw new ValidationErrorsException("Invalid project name");
        }

        UUID id = projectDao.getId(teamId, projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }

        return projectManager.assertProjectAccess(id, requiredRole, teamMembersOnly);
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Not authorized");
        }
    }
}
