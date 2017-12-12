package com.walmartlabs.concord.server.org.triggers;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
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

import static com.walmartlabs.concord.server.org.project.RepositoryUtils.assertRepository;

@Named
public class TriggerResourceImpl extends AbstractDao implements TriggerResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(TriggerResourceImpl.class);

    private final ProjectLoader projectLoader = new ProjectLoader();
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final TriggersDao triggersDao;
    private final RepositoryManager repositoryManager;
    private final ProjectAccessManager projectAccessManager;
    private final OrganizationManager orgManager;

    @Inject
    public TriggerResourceImpl(ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               TriggersDao triggersDao,
                               RepositoryManager repositoryManager,
                               Configuration cfg,
                               ProjectAccessManager projectAccessManager,
                               OrganizationManager orgManager) {

        super(cfg);
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.triggersDao = triggersDao;
        this.repositoryManager = repositoryManager;
        this.projectAccessManager = projectAccessManager;
        this.orgManager = orgManager;
    }

    @Override
    public List<TriggerEntry> list(String orgName, String projectName, String repositoryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        ProjectEntry p = assertProject(org.getId(), projectName, ResourceAccessLevel.READER, true);
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
    public Response refresh(String orgName, String projectName, String repositoryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        ProjectEntry p = assertProject(org.getId(), projectName, ResourceAccessLevel.WRITER, true);
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

    private ProjectEntry assertProject(UUID orgId, String projectName, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (projectName == null) {
            throw new ValidationErrorsException("Invalid project name");
        }

        UUID id = projectDao.getId(orgId, projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }

        return projectAccessManager.assertProjectAccess(id, accessLevel, orgMembersOnly);
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Not authorized");
        }
    }
}
