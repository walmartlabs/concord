package com.walmartlabs.concord.server.project;

import com.google.common.base.Splitter;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.PerformedActionType;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.events.GithubWebhookService;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import com.walmartlabs.concord.server.team.TeamDao;
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

@Named
public class ProjectResourceImpl extends AbstractDao implements ProjectResource, Resource {

    private final TeamDao teamDao;
    private final ProjectDao projectDao;
    private final SecretManager secretManager;
    private final RepositoryDao repositoryDao;
    private final SecretDao secretDao;
    private final Set<ConfigurationValidator> cfgValidators;
    private final GithubWebhookService githubWebhookService;

    private final Map<String, Field<?>> key2ProjectField;
    private final Map<String, Field<?>> key2RepositoryField;

    @Inject
    public ProjectResourceImpl(Configuration cfg,
                               TeamDao teamDao, ProjectDao projectDao,
                               SecretManager secretManager,
                               RepositoryDao repositoryDao,
                               SecretDao secretDao,
                               Set<ConfigurationValidator> cfgValidators,
                               GithubWebhookService githubWebhookService) {

        super(cfg);

        this.teamDao = teamDao;
        this.projectDao = projectDao;
        this.secretManager = secretManager;
        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.cfgValidators = cfgValidators;
        this.githubWebhookService = githubWebhookService;

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
        UUID teamId = assertOptionalTeam(req.getTeamId(), req.getTeamName());

        Map<String, Object> cfg = req.getCfg();
        validateCfg(cfg);

        String projectName = req.getName();
        UUID projectId = projectDao.getId(projectName);

        if (projectId != null) {
            UpdateProjectRequest up = new UpdateProjectRequest(req);
            update(projectName, up);
            return new CreateProjectResponse(PerformedActionType.UPDATED);
        }

        assertPermissions(projectName, Permissions.PROJECT_CREATE_NEW,
                "The current user does not have permissions to create a new project");

        Map<String, UpdateRepositoryRequest> repos = req.getRepositories();
        if (repos != null) {
            for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
                String secret = r.getValue().getSecret();
                assertSecret(secret);
            }
        }

        UUID tId = teamId;
        InsertResult r = txResult(tx -> {
            UUID pId = projectDao.insert(tx, projectName, req.getDescription(), tId, cfg);

            Map<String, UUID> rIds = Collections.emptyMap();
            if (repos != null) {
                rIds = insert(tx, pId, repos);
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

        return new CreateProjectResponse(PerformedActionType.CREATED);
    }

    private UUID assertOptionalTeam(UUID teamId, String teamName) {
        if (teamId != null) {
            if (teamDao.get(teamId) == null) {
                throw new ValidationErrorsException("Team not found: " + teamId);
            }
        }

        if (teamId == null && teamName != null) {
            teamId = teamDao.getId(teamName);
            if (teamId == null) {
                throw new ValidationErrorsException("Team not found: " + teamName);
            }
        }

        return teamId;
    }

    @Override
    @Validate
    public CreateRepositoryResponse createRepository(String projectName, CreateRepositoryRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        assertProject(projectName);
        UUID secretId = assertSecret(request.getSecret());
        UUID projectId = projectDao.getId(projectName);

        UUID repoId = txResult(tx -> repositoryDao.insert(tx, projectId, request.getName(), request.getUrl(),
                request.getBranch(), request.getCommitId(),
                request.getPath(), secretId));

        githubWebhookService.register(projectId, repoId, request.getUrl());

        return new CreateRepositoryResponse();
    }

    @Override
    @Validate
    public ProjectEntry get(String projectName) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        ProjectEntry e = projectDao.getByName(projectName);
        if (e == null) {
            throw new WebApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }
        return e;
    }

    @Override
    @Validate
    public RepositoryEntry getRepository(String projectName, String repositoryName) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        UUID projectId = assertProject(projectName);
        UUID repoId = assertRepository(projectId, repositoryName);

        return repositoryDao.get(projectId, repoId);
    }

    @Override
    @Validate
    public List<ProjectEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2ProjectField.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return projectDao.list(sortField, asc);
    }

    @Override
    @Validate
    public List<RepositoryEntry> listRepositories(String projectName, String sortBy, boolean asc) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        UUID projectId = assertProject(projectName);

        Field<?> sortField = key2RepositoryField.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return repositoryDao.list(projectId, sortField, asc);
    }

    @Override
    @Validate
    public UpdateProjectResponse update(String projectName, UpdateProjectRequest req) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        UUID teamId = assertOptionalTeam(req.getTeamId(), req.getTeamName());

        UUID projectId = assertProject(projectName);

        Map<String, Object> cfg = req.getCfg();
        validateCfg(cfg);

        Map<String, UpdateRepositoryRequest> repos = req.getRepositories();
        if (repos != null) {
            for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
                String secret = r.getValue().getSecret();
                assertSecret(secret);
            }

            githubWebhookService.unregister(projectId);
        }

        Map<String, UUID> rIds = txResult(tx -> {
            projectDao.update(tx, projectId, projectName, req.getDescription(), teamId, cfg);

            Map<String, UUID> m = Collections.emptyMap();
            if (repos != null) {
                repositoryDao.deleteAll(tx, projectId);
                m = insert(tx, projectId, repos);
            }
            return m;
        });

        if(repos != null) {
            repos.forEach((repoName, updateRepositoryRequest) ->
                    githubWebhookService.register(projectId, rIds.get(repoName), updateRepositoryRequest.getUrl()));
        }

        return new UpdateProjectResponse();
    }

    @Override
    @Validate
    public UpdateRepositoryResponse updateRepository(String projectName, String repositoryName, UpdateRepositoryRequest req) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        UUID projectId = assertProject(projectName);
        UUID repoId = assertRepository(projectId, repositoryName);
        UUID secretId = assertSecret(req.getSecret());

        githubWebhookService.unregister(projectId, repoId);

        tx(tx -> repositoryDao.update(tx, repoId,
                repositoryName, req.getUrl(),
                trim(req.getBranch()), trim(req.getCommitId()),
                trim(req.getPath()), secretId));

        githubWebhookService.register(projectId, repoId, req.getUrl());

        return new UpdateRepositoryResponse();
    }

    @Override
    @Validate
    @SuppressWarnings("unchecked")
    public Object getConfiguration(String projectName, String path) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        UUID projectId = assertProject(projectName);

        String[] ps = cfgPath(path);
        Object v = projectDao.getConfigurationValue(projectId, ps);

        if (v == null) {
            throw new WebApplicationException("Value not found: " + path, Status.NOT_FOUND);
        }

        return v;
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, String path, Object data) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        UUID projectId = assertProject(projectName);

        Map<String, Object> cfg = projectDao.getConfiguration(projectId);
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
        tx(tx -> projectDao.update(tx, projectId, newCfg));
        return new UpdateProjectConfigurationResponse();
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, Object data) {
        return updateConfiguration(projectName, "/", data);
    }

    @Override
    public DeleteProjectConfigurationResponse deleteConfiguration(String projectName, String path) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        UUID projectId = assertProject(projectName);

        Map<String, Object> cfg = projectDao.getConfiguration(projectId);
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
        tx(tx -> projectDao.update(tx, projectId, newCfg));
        return new DeleteProjectConfigurationResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(String projectName) {
        assertPermissions(projectName, Permissions.PROJECT_DELETE_INSTANCE,
                "The current user does not have permissions to delete the specified project");

        UUID projectId = assertProject(projectName);

        githubWebhookService.unregister(projectId);

        tx(tx -> projectDao.delete(tx, projectId));
        return new DeleteProjectResponse();
    }

    @Override
    @Validate
    public DeleteRepositoryResponse deleteRepository(String projectName, String repositoryName) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        UUID projectId = assertProject(projectName);
        UUID repoId = assertRepository(projectId, repositoryName);

        githubWebhookService.unregister(projectId, repoId);

        tx(tx -> repositoryDao.delete(tx, repoId));

        return new DeleteRepositoryResponse();
    }

    @Override
    @Validate
    @RequiresAuthentication
    public EncryptValueResponse encrypt(String projectName, EncryptValueRequest req) {
        if (req.getValue() == null) {
            throw new ValidationErrorsException("Value is required");
        }

        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        assertProject(projectName);

        byte[] input = req.getValue().getBytes();
        byte[] result = secretManager.encryptData(projectName, input);

        return new EncryptValueResponse(result);
    }

    private void assertPermissions(String projectName, String wildcard, String message) {
        Subject subject = SecurityUtils.getSubject();
        String perm = String.format(wildcard, projectName);
        if (!subject.isPermitted(perm)) {
            throw new UnauthorizedException(message);
        }
    }

    private UUID assertSecret(String name) {
        if (name == null) {
            return null;
        }

        UUID id = secretDao.getId(name);
        if (id == null) {
            throw new ValidationErrorsException("Secret not found: " + name);
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.SECRET_READ_INSTANCE, name))) {
            throw new UnauthorizedException("The current user does not have permissions to use the specified secret");
        }

        return id;
    }

    private UUID assertProject(String projectName) {
        if (projectName == null) {
            throw new ValidationErrorsException("Invalid project name");
        }

        UUID id = projectDao.getId(projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }
        return id;
    }

    private UUID assertRepository(UUID projectId, String repositoryName) {
        if (repositoryName == null) {
            throw new ValidationErrorsException("Invalid repository name");
        }

        UUID id = repositoryDao.getId(projectId, repositoryName);
        if (id == null) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }
        return id;
    }

    private Map<String, UUID> insert(DSLContext tx, UUID projectId, Map<String, UpdateRepositoryRequest> repos) {
        Map<String, UUID> ids = new HashMap<>();

        for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
            String name = r.getKey();
            UpdateRepositoryRequest req = r.getValue();

            UUID secretId = assertSecret(req.getSecret());

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
