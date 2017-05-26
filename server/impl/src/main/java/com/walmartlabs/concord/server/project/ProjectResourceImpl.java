package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.template.TemplateResolver;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
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

import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;

@Named
public class ProjectResourceImpl extends AbstractDao implements ProjectResource, Resource {

    private final ProjectDao projectDao;
    private final ProjectConfigurationDao configurationDao;
    private final RepositoryDao repositoryDao;
    private final TemplateResolver templateResolver;
    private final SecretDao secretDao;
    private final Set<ConfigurationValidator> cfgValidators;

    private final Map<String, Field<?>> key2ProjectField;
    private final Map<String, Field<?>> key2RepositoryField;

    @Inject
    public ProjectResourceImpl(Configuration cfg, ProjectDao projectDao, ProjectConfigurationDao configurationDao,
                               RepositoryDao repositoryDao, TemplateResolver templateResolver, SecretDao secretDao,
                               Set<ConfigurationValidator> cfgValidators) {

        super(cfg);

        this.projectDao = projectDao;
        this.configurationDao = configurationDao;
        this.repositoryDao = repositoryDao;
        this.templateResolver = templateResolver;
        this.secretDao = secretDao;
        this.cfgValidators = cfgValidators;

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
    public CreateProjectResponse createOrUpdate(ProjectEntry request) {
        assertTemplates(request.getTemplates());

        Map<String, Object> cfg = request.getCfg();
        validateCfg(cfg);

        String projectName = request.getName();
        if (projectDao.exists(projectName)) {
            UpdateProjectRequest req = new UpdateProjectRequest(request.getDescription(), request.getTemplates(),
                    request.getRepositories(), request.getCfg());
            update(projectName, req);
            return new CreateProjectResponse(false);
        }

        assertPermissions(projectName, Permissions.PROJECT_CREATE_NEW,
                "The current user does not have permissions to create a new project");

        Map<String, UpdateRepositoryRequest> repos = request.getRepositories();
        if (repos != null) {
            for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
                String secret = r.getValue().getSecret();
                assertSecret(secret);
            }
        }

        tx(tx -> {
            projectDao.insert(tx, projectName, request.getDescription(), request.getTemplates());

            if (repos != null) {
                insert(tx, projectName, repos);
            }

            if (cfg != null) {
                configurationDao.insert(tx, projectName, cfg);
            }
        });
        return new CreateProjectResponse(true);
    }

    @Override
    @Validate
    public CreateRepositoryResponse createRepository(String projectName, CreateRepositoryRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        assertProject(projectName);
        assertSecret(request.getSecret());

        tx(tx -> repositoryDao.insert(tx, projectName, request.getName(), request.getUrl(), request.getBranch(), request.getCommitId(), request.getSecret()));
        return new CreateRepositoryResponse();
    }

    @Override
    @Validate
    public ProjectEntry get(String projectName) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        ProjectEntry e = projectDao.get(projectName);
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

        assertProject(projectName);

        return repositoryDao.get(projectName, repositoryName);
    }

    @Override
    @Validate
    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfiguration(String projectName, String path) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        assertProject(projectName);

        String[] ps = path != null ? path.split("/") : null;
        Object v = configurationDao.getValue(projectName, ps);

        if (v != null && !(v instanceof Map)) {
            throw new WebApplicationException("Path should point to a JSON object, got: " + v.getClass(), Status.BAD_REQUEST);
        }

        return (Map<String, Object>) v;
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

        assertProject(projectName);

        Field<?> sortField = key2RepositoryField.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return repositoryDao.list(projectName, sortField, asc);
    }

    @Override
    @Validate
    public UpdateProjectResponse update(String projectName, UpdateProjectRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        assertProject(projectName);
        assertTemplates(request.getTemplates());

        Map<String, Object> cfg = request.getCfg();
        validateCfg(cfg);

        Map<String, UpdateRepositoryRequest> repos = request.getRepositories();
        if (repos != null) {
            for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
                String secret = r.getValue().getSecret();
                assertSecret(secret);
            }
        }

        tx(tx -> {
            projectDao.update(tx, projectName, request.getDescription(), request.getTemplates());

            if (repos != null) {
                repositoryDao.deleteAll(tx, projectName);
                insert(tx, projectName, repos);
            }

            if (cfg != null) {
                configurationDao.update(tx, projectName, cfg);
            }
        });

        return new UpdateProjectResponse();
    }

    @Override
    @Validate
    public UpdateRepositoryResponse updateRepository(String projectName, String repositoryName, UpdateRepositoryRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        assertRepository(projectName, repositoryName);
        assertSecret(request.getSecret());

        tx(tx -> repositoryDao.update(tx, repositoryName, request.getUrl(), request.getBranch(), request.getCommitId(), request.getSecret()));
        return new UpdateRepositoryResponse();
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, String path, Map<String, Object> data) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        assertProject(projectName);

        Map<String, Object> cfg = configurationDao.get(projectName);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        String[] ps = path != null ? path.split("/") : null;
        ConfigurationUtils.merge(cfg, data, ps);

        validateCfg(cfg);

        Map<String, Object> newCfg = cfg;
        tx(tx -> configurationDao.update(tx, projectName, newCfg));
        return new UpdateProjectConfigurationResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(String projectName) {
        assertPermissions(projectName, Permissions.PROJECT_DELETE_INSTANCE,
                "The current user does not have permissions to delete the specified project");

        tx(tx -> projectDao.delete(tx, projectName));
        return new DeleteProjectResponse();
    }

    @Override
    @Validate
    public DeleteRepositoryResponse deleteRepository(String projectName, String repositoryName) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        assertRepository(projectName, repositoryName);

        tx(tx -> repositoryDao.delete(tx, repositoryName));
        return new DeleteRepositoryResponse();
    }

    private void assertTemplates(Collection<String> templateNames) {
        if (templateNames == null || templateNames.isEmpty()) {
            return;
        }

        templateNames.forEach(t -> {
            assertTemplatePermissions(t);
            if (!templateResolver.exists(t)) {
                throw new ValidationErrorsException("Unknown template: " + t);
            }
        });
    }

    private void assertPermissions(String projectName, String wildcard, String message) {
        Subject subject = SecurityUtils.getSubject();
        String perm = String.format(wildcard, projectName);
        if (!subject.isPermitted(perm)) {
            throw new UnauthorizedException(message);
        }
    }

    private void assertSecret(String name) {
        if (name == null) {
            return;
        }

        if (!secretDao.exists(name)) {
            throw new ValidationErrorsException("Secret not found: " + name);
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.SECRET_READ_INSTANCE, name))) {
            throw new UnauthorizedException("The current user does not have permissions to use the specified secret");
        }
    }

    private void assertProject(String projectName) {
        if (!projectDao.exists(projectName)) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }
    }

    private void assertRepository(String projectName, String repositoryName) {
        if (!repositoryDao.exists(projectName, repositoryName)) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }
    }

    private void insert(DSLContext tx, String projectName, Map<String, UpdateRepositoryRequest> repos) {
        for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
            String name = r.getKey();
            repositoryDao.insert(tx, projectName, name, r.getValue().getUrl(), r.getValue().getBranch(), r.getValue().getCommitId(), r.getValue().getSecret());
        }
    }

    private void validateCfg(Map<String, Object> cfg) {
        if (cfg == null) {
            return;
        }

        for (ConfigurationValidator v : cfgValidators) {
            v.validate(cfg);
        }
    }

    private static void assertTemplatePermissions(String name) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.TEMPLATE_USE_INSTANCE, name))) {
            throw new UnauthorizedException("The current user does not have permissions to use the template: " + name);
        }
    }
}
