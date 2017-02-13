package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.template.TemplateDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;

@Named
public class ProjectResourceImpl extends AbstractDao implements ProjectResource, Resource {

    private final ProjectDao projectDao;
    private final ProjectConfigurationDao configurationDao;
    private final RepositoryDao repositoryDao;
    private final TemplateDao templateDao;
    private final SecretDao secretDao;

    private final Map<String, Field<?>> key2ProjectField;
    private final Map<String, Field<?>> key2RepositoryField;

    @Inject
    public ProjectResourceImpl(Configuration cfg, ProjectDao projectDao, ProjectConfigurationDao configurationDao,
                               RepositoryDao repositoryDao, TemplateDao templateDao, SecretDao secretDao) {

        super(cfg);

        this.projectDao = projectDao;
        this.configurationDao = configurationDao;
        this.repositoryDao = repositoryDao;
        this.templateDao = templateDao;
        this.secretDao = secretDao;

        this.key2ProjectField = new HashMap<>();
        key2ProjectField.put("projectId", PROJECTS.PROJECT_ID);
        key2ProjectField.put("name", PROJECTS.PROJECT_NAME);

        this.key2RepositoryField = new HashMap<>();
        key2RepositoryField.put("name", REPOSITORIES.REPO_NAME);
        key2RepositoryField.put("url", REPOSITORIES.REPO_URL);
        key2RepositoryField.put("branch", REPOSITORIES.REPO_BRANCH);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.PROJECT_CREATE_NEW)
    public CreateProjectResponse create(CreateProjectRequest request) {
        if (projectDao.exists(request.getName())) {
            throw new ValidationErrorsException("Project already exists: " + request.getName());
        }

        Collection<String> templateIds = getTemplateIds(request.getTemplates());
        String projectId = UUID.randomUUID().toString();

        tx(tx -> {
            projectDao.insert(tx, projectId, request.getName(), templateIds);

            Map<String, UpdateRepositoryRequest> repos = request.getRepositories();
            if (repos != null) {
                insert(tx, projectId, repos);
            }

            Map<String, Object> cfg = request.getCfg();
            if (cfg != null) {
                configurationDao.insert(tx, projectId, cfg);
            }
        });

        return new CreateProjectResponse(projectId);
    }

    @Override
    @Validate
    public CreateRepositoryResponse createRepository(String projectName, CreateRepositoryRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        String projectId = resolveProjectId(projectName);
        String secretId = resolveSecretId(request.getSecret());
        tx(tx -> {
            repositoryDao.insert(tx, projectId, request.getName(), request.getUrl(), request.getBranch(), secretId);
        });
        return new CreateRepositoryResponse();
    }

    @Override
    @Validate
    public ProjectEntry get(String projectName) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");
        return projectDao.getByName(projectName);
    }

    @Override
    @Validate
    public RepositoryEntry getRepository(String projectName, String repositoryName) {
        assertPermissions(projectName, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");

        String projectId = resolveProjectId(projectName);
        return repositoryDao.getByNameInProject(projectId, repositoryName);
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

        String projectId = resolveProjectId(projectName);
        Field<?> sortField = key2RepositoryField.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return repositoryDao.list(projectId, sortField, asc);
    }

    @Override
    @Validate
    public UpdateProjectResponse update(String projectName, UpdateProjectRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        String projectId = resolveProjectId(projectName);
        Collection<String> templateIds = getTemplateIds(request.getTemplates());
        tx(tx -> {
            projectDao.update(tx, projectId, templateIds);

            Map<String, UpdateRepositoryRequest> repos = request.getRepositories();
            if (repos != null) {
                repositoryDao.deleteAll(tx, projectId);
                insert(tx, projectId, repos);
            }

            Map<String, Object> cfg = request.getCfg();
            if (cfg != null) {
                configurationDao.delete(tx, projectId);
                configurationDao.insert(tx, projectId, cfg);
            }
        });

        return new UpdateProjectResponse();
    }

    @Override
    public UpdateRepositoryResponse updateRepository(String projectName, String repositoryName, UpdateRepositoryRequest request) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        String projectId = resolveProjectId(projectName);
        assertRepository(projectId, repositoryName);

        String secretId = resolveSecretId(request.getSecret());
        tx(tx -> {
            repositoryDao.update(tx, repositoryName, request.getUrl(), request.getBranch(), secretId);
        });
        return new UpdateRepositoryResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(String projectName) {
        assertPermissions(projectName, Permissions.PROJECT_DELETE_INSTANCE,
                "The current user does not have permissions to delete the specified project");

        String projectId = resolveProjectId(projectName);
        tx(tx -> {
            projectDao.delete(tx, projectId);
        });
        return new DeleteProjectResponse();
    }

    @Override
    public DeleteRepositoryResponse deleteRepository(String projectName, String repositoryName) {
        assertPermissions(projectName, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        String projectId = resolveProjectId(projectName);
        assertRepository(projectId, repositoryName);

        tx(tx -> {
            repositoryDao.delete(tx, repositoryName);
        });
        return new DeleteRepositoryResponse();
    }

    private Collection<String> getTemplateIds(Collection<String> templateNames) {
        return mapToList(templateNames, n -> {
            assertTemplatePermissions(n);

            String id = templateDao.getId(n);
            if (id == null) {
                throw new ValidationErrorsException("Unknown template: " + n);
            }
            return id;
        });
    }

    private void assertPermissions(String projectName, String wildcard, String message) {
        Subject subject = SecurityUtils.getSubject();
        String perm = String.format(wildcard, projectName);
        if (!subject.isPermitted(perm)) {
            throw new UnauthorizedException(message);
        }
    }

    private String resolveProjectId(String projectName) {
        String id = projectDao.getId(projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }
        return id;
    }

    private String resolveSecretId(String name) {
        if (name == null) {
            return null;
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.SECRET_READ_INSTANCE, name))) {
            throw new UnauthorizedException("The current user does not have permissions to use the specified secret");
        }

        String id = secretDao.getId(name);
        if (id == null) {
            throw new ValidationErrorsException("Secret not found: " + id);
        }

        return id;
    }

    private void assertRepository(String projectId, String repositoryName) {
        if (!repositoryDao.exists(projectId, repositoryName)) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }
    }

    private void insert(DSLContext tx, String projectId, Map<String, UpdateRepositoryRequest> repos) {
        for (Map.Entry<String, UpdateRepositoryRequest> r : repos.entrySet()) {
            String name = r.getKey();
            String secretId = resolveSecretId(r.getValue().getSecret());
            repositoryDao.insert(tx, projectId, name,
                    r.getValue().getUrl(), r.getValue().getBranch(), secretId);
        }
    }

    private static void assertTemplatePermissions(String name) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.TEMPLATE_USE_INSTANCE, name))) {
            throw new UnauthorizedException("The current user does not have permissions to use the template: " + name);
        }
    }
}
