package com.walmartlabs.concord.server.project;

import com.google.common.base.Splitter;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.PerformedActionType;
import com.walmartlabs.concord.server.api.events.EventResource;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.events.Events;
import com.walmartlabs.concord.server.events.GithubWebhookService;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import com.walmartlabs.concord.server.team.TeamManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.subject.Subject;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.project.RepositoryUtils.assertRepository;

@Named
public class ProjectResourceImpl extends AbstractDao implements ProjectResource, Resource {

    private final TeamManager teamManager;
    private final ProjectDao projectDao;
    private final ProjectManager projectManager;
    private final SecretManager secretManager;
    private final RepositoryDao repositoryDao;
    private final SecretDao secretDao;
    private final Set<ConfigurationValidator> cfgValidators;
    private final GithubWebhookService githubWebhookService;
    private final EventResource eventResource;

    private final Map<String, Field<?>> key2ProjectField;
    private final Map<String, Field<?>> key2RepositoryField;

    @Inject
    public ProjectResourceImpl(Configuration cfg,
                               TeamManager teamManager,
                               ProjectDao projectDao,
                               ProjectManager projectManager,
                               SecretManager secretManager,
                               RepositoryDao repositoryDao,
                               SecretDao secretDao,
                               Set<ConfigurationValidator> cfgValidators,
                               GithubWebhookService githubWebhookService,
                               EventResource eventResource) {

        super(cfg);

        this.teamManager = teamManager;
        this.projectDao = projectDao;
        this.projectManager = projectManager;
        this.secretManager = secretManager;
        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.cfgValidators = cfgValidators;
        this.githubWebhookService = githubWebhookService;
        this.eventResource = eventResource;

        this.key2ProjectField = new HashMap<>();
        key2ProjectField.put("name", PROJECTS.PROJECT_NAME);
        key2ProjectField.put("description", PROJECTS.DESCRIPTION);

        this.key2RepositoryField = new HashMap<>();
        key2RepositoryField.put("name", REPOSITORIES.REPO_NAME);
        key2RepositoryField.put("url", REPOSITORIES.REPO_URL);
        key2RepositoryField.put("branch", REPOSITORIES.REPO_BRANCH);
    }

    @Override
    @Validate
    public CreateProjectResponse createOrUpdate(ProjectEntry req) {
        String projectName = req.getName();

        TeamEntry t = teamManager.assertTeam(req.getTeamId(), req.getTeamName());
        UUID projectId = projectDao.getId(t.getId(), projectName);

        if (projectId != null) {
            projectManager.assertProjectAccess(projectId, TeamRole.WRITER, true);
            update(projectName, new UpdateProjectRequest(req));
            return new CreateProjectResponse(projectId, PerformedActionType.UPDATED);
        }

        teamManager.assertTeamAccess(t.getId(), TeamRole.WRITER, true);

        Map<String, Object> cfg = req.getCfg();
        validateCfg(cfg);

        Map<String, RepositoryEntry> repos = req.getRepositories();
        if (repos != null) {
            for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
                String secret = r.getValue().getSecret();
                assertSecret(t.getId(), secret);
            }
        }

        InsertResult r = txResult(tx -> {
            UUID pId = projectDao.insert(tx, t.getId(), projectName, req.getDescription(), cfg, req.getVisibility());

            Map<String, UUID> rIds = Collections.emptyMap();
            if (repos != null) {
                rIds = insert(tx, t.getId(), pId, repos);
            }

            return new InsertResult(pId, rIds);
        });

        if (repos != null) {
            UUID pId = r.projectId;
            repos.forEach((k, v) -> {
                UUID rId = r.repoIds.get(k);
                githubWebhookService.register(pId, rId, v.getUrl());
            });
        }

        return new CreateProjectResponse(r.projectId, PerformedActionType.CREATED);
    }

    @Override
    @Validate
    public CreateRepositoryResponse createRepository(String projectName, RepositoryEntry request) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry project = assertProject(teamId, projectName, TeamRole.WRITER, true);

        UUID secretId = assertSecret(project.getTeamId(), request.getSecret());

        UUID repoId = txResult(tx -> repositoryDao.insert(tx, project.getId(), request.getName(), request.getUrl(),
                request.getBranch(), request.getCommitId(),
                request.getPath(), secretId));

        // TODO: register using the triggered flow?
        githubWebhookService.register(project.getId(), repoId, request.getUrl());

        eventResource.event(Events.CONCORD_EVENT, Events.Repository.repositoryCreated(projectName, request.getName()));

        return new CreateRepositoryResponse();
    }

    @Override
    @Validate
    public ProjectEntry get(String projectName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        return assertProject(teamId, projectName, TeamRole.READER, false);
    }

    @Override
    @Validate
    public RepositoryEntry getRepository(String projectName, String repositoryName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.READER, false);
        return assertRepository(p, repositoryName);
    }

    @Override
    @Validate
    public List<ProjectEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2ProjectField.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }

        return projectManager.list(sortField, asc);
    }

    @Override
    @Validate
    public List<RepositoryEntry> listRepositories(String projectName, String sortBy, boolean asc) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.READER, false);

        Field<?> sortField = key2RepositoryField.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }

        // can be replaced with the data from the project entry retrieved above
        return repositoryDao.list(p.getId(), sortField, asc);
    }

    @Override
    @Validate
    public UpdateProjectResponse update(String projectName, UpdateProjectRequest req) {
        TeamEntry t = teamManager.assertTeam(req.getTeamId(), req.getTeamName());
        ProjectEntry p = assertProject(t.getId(), projectName, TeamRole.WRITER, true);

        Map<String, Object> cfg = req.getCfg();
        validateCfg(cfg);

        Map<String, RepositoryEntry> repos = req.getRepositories();
        if (repos != null) {
            for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
                String secret = r.getValue().getSecret();
                assertSecret(t.getId(), secret);
            }

            githubWebhookService.unregister(p.getId());
        }

        Map<String, UUID> rIds = txResult(tx -> {
            projectDao.update(tx, t.getId(), p.getId(), projectName, req.getDescription(), cfg);

            Map<String, UUID> m = Collections.emptyMap();
            if (repos != null) {
                repositoryDao.deleteAll(tx, p.getId());
                m = insert(tx, t.getId(), p.getId(), repos);
            }
            return m;
        });

        if (repos != null) {
            repos.forEach((repoName, RepositoryEntry) ->
                    githubWebhookService.register(p.getId(), rIds.get(repoName), RepositoryEntry.getUrl()));
        }

        return new UpdateProjectResponse();
    }

    @Override
    @Validate
    public UpdateRepositoryResponse updateRepository(String projectName, String repositoryName, RepositoryEntry request) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);
        UUID secretId = assertSecret(p.getTeamId(), request.getSecret());

        githubWebhookService.unregister(p.getId(), r.getId());

        tx(tx -> repositoryDao.update(tx, r.getId(),
                repositoryName, request.getUrl(),
                trim(request.getBranch()), trim(request.getCommitId()),
                trim(request.getPath()), secretId));

        // TODO: register using the triggered flow?
        githubWebhookService.register(p.getId(), r.getId(), request.getUrl());

        eventResource.event(Events.CONCORD_EVENT, Events.Repository.repositoryUpdated(projectName, request.getName()));

        return new UpdateRepositoryResponse();
    }

    @Override
    @Validate
    @SuppressWarnings("unchecked")
    public Object getConfiguration(String projectName, String path) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.READER, false);

        String[] ps = cfgPath(path);
        Object v = projectDao.getConfigurationValue(p.getId(), ps);

        if (v == null) {
            throw new WebApplicationException("Value not found: " + path, Status.NOT_FOUND);
        }

        return v;
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, String path, Object data) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);

        Map<String, Object> cfg = projectDao.getConfiguration(p.getId());
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        String[] ps = cfgPath(path);
        if (ps == null || ps.length < 1) {
            if (!(data instanceof Map)) {
                throw new ValidationErrorsException("Expected a JSON object: " + data);
            }
            cfg = (Map<String, Object>) data;
        } else {
            ConfigurationUtils.set(cfg, data, ps);
        }

        validateCfg(cfg);

        Map<String, Object> newCfg = cfg;
        tx(tx -> projectDao.update(tx, p.getId(), newCfg));
        return new UpdateProjectConfigurationResponse();
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, Object data) {
        return updateConfiguration(projectName, "/", data);
    }

    @Override
    public DeleteProjectConfigurationResponse deleteConfiguration(String projectName, String path) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);

        Map<String, Object> cfg = projectDao.getConfiguration(p.getId());
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        String[] ps = cfgPath(path);
        if (ps == null || ps.length == 0) {
            cfg = null;
        } else {
            ConfigurationUtils.delete(cfg, ps);
        }

        validateCfg(cfg);

        Map<String, Object> newCfg = cfg;
        tx(tx -> projectDao.update(tx, p.getId(), newCfg));
        return new DeleteProjectConfigurationResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(String projectName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);

        githubWebhookService.unregister(p.getId());

        tx(tx -> projectDao.delete(tx, p.getId()));
        return new DeleteProjectResponse();
    }

    @Override
    @Validate
    public DeleteRepositoryResponse deleteRepository(String projectName, String repositoryName) {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        ProjectEntry p = assertProject(teamId, projectName, TeamRole.WRITER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);

        githubWebhookService.unregister(p.getId(), r.getId());

        tx(tx -> repositoryDao.delete(tx, r.getId()));

        return new DeleteRepositoryResponse();
    }

    @Override
    @Validate
    @RequiresAuthentication
    public EncryptValueResponse encrypt(String projectName, EncryptValueRequest req) {
        if (req.getValue() == null) {
            throw new ValidationErrorsException("Value is required");
        }

        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        assertProject(teamId, projectName, TeamRole.READER, true);

        byte[] input = req.getValue().getBytes();
        byte[] result = secretManager.encryptData(projectName, input);

        return new EncryptValueResponse(result);
    }

    private UUID assertSecret(UUID teamId, String name) {
        if (name == null) {
            return null;
        }

        UUID id = secretDao.getId(teamId, name);
        if (id == null) {
            throw new ValidationErrorsException("Secret not found: " + name);
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.SECRET_READ_INSTANCE, name))) {
            throw new UnauthorizedException("The current user does not have permissions to use the specified secret");
        }

        return id;
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

    private Map<String, UUID> insert(DSLContext tx, UUID teamId, UUID projectId, Map<String, RepositoryEntry> repos) {
        Map<String, UUID> ids = new HashMap<>();

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String name = r.getKey();
            RepositoryEntry req = r.getValue();

            UUID secretId = assertSecret(teamId, req.getSecret());

            UUID id = repositoryDao.insert(tx, projectId, name, req.getUrl(),
                    trim(req.getBranch()),
                    trim(req.getCommitId()),
                    trim(req.getPath()),
                    secretId);

            ids.put(name, id);
        }

        return ids;
    }

    private String trim(String s) {
        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.isEmpty()) {
            return null;
        }

        return s;
    }

    private void validateCfg(Map<String, Object> cfg) {
        if (cfg == null) {
            return;
        }

        for (ConfigurationValidator v : cfgValidators) {
            v.validate(cfg);
        }
    }

    private static String[] cfgPath(String s) {
        if (s == null) {
            return new String[0];
        }

        List<String> l = Splitter.on("/").omitEmptyStrings().splitToList(s);
        return l.toArray(new String[l.size()]);
    }

    private static final class InsertResult {

        private final UUID projectId;
        private final Map<String, UUID> repoIds;

        private InsertResult(UUID projectId, Map<String, UUID> repoIds) {
            this.projectId = projectId;
            this.repoIds = repoIds;
        }
    }
}
