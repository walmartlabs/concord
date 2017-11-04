package com.walmartlabs.concord.server.triggers;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.api.trigger.TriggerResource;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.ProjectManager;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import org.jooq.Configuration;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Named
public class TriggerResourceImpl extends AbstractDao implements TriggerResource, Resource {

    private final ProjectLoader projectLoader = new ProjectLoader();

    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;

    private final RepositoryManager repositoryManager;
    private final ProjectManager projectManager;

    @Inject
    public TriggerResourceImpl(ProjectDao projectDao, TriggersDao triggersDao,
                               RepositoryManager repositoryManager,
                               ProjectManager projectManager,
                               Configuration cfg) {
        super(cfg);
        this.projectDao = projectDao;
        this.triggersDao = triggersDao;
        this.repositoryManager = repositoryManager;
        this.projectManager = projectManager;
    }

    @Override
    public void refresh(String projectName, String repositoryName) {
        ProjectEntry p = assertProject(projectName, TeamRole.WRITER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);

        Path repoPath = repositoryManager.fetch(p.getId(), r);

        ProjectDefinition pd;
        try {
            pd = projectLoader.load(repoPath);
        } catch (IOException e) {
            throw new WebApplicationException("load project '" + projectName + "' error", e);
        }

        tx(tx -> {
            triggersDao.delete(tx, p.getId(), r.getId());
            pd.getTriggers().forEach(t -> {
                triggersDao.insert(tx, p.getId(), r.getId(), t.getName(), t.getEntryPoint(), t.getArguments(), t.getParams());
            });
        });
    }

    private ProjectEntry assertProject(String projectName, TeamRole requiredRole, boolean teamMembersOnly) {
        if (projectName == null) {
            throw new ValidationErrorsException("Invalid project name");
        }

        UUID id = projectDao.getId(projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }

        return projectManager.assertProjectAccess(id, requiredRole, teamMembersOnly);
    }

    private RepositoryEntry assertRepository(ProjectEntry p, String repositoryName) {
        if (repositoryName == null) {
            throw new ValidationErrorsException("Invalid repository name");
        }

        Map<String, RepositoryEntry> repos = p.getRepositories();
        if (repos == null || repos.isEmpty()) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }

        RepositoryEntry r = repos.get(repositoryName);
        if (r == null) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }

        return r;
    }

}
