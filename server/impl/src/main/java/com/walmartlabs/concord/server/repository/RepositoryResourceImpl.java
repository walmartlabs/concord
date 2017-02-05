package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.repository.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.project.ProjectDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;

@Named
public class RepositoryResourceImpl implements RepositoryResource, Resource {

    private final RepositoryDao repositoryDao;
    private final ProjectDao projectDao;

    private final Map<String, Field<?>> key2Field;

    @Inject
    public RepositoryResourceImpl(RepositoryDao repositoryDao, ProjectDao projectDao) {
        this.repositoryDao = repositoryDao;
        this.projectDao = projectDao;

        this.key2Field = new HashMap<>();
        key2Field.put("id", REPOSITORIES.REPO_ID);
        key2Field.put("name", REPOSITORIES.REPO_NAME);
        key2Field.put("url", REPOSITORIES.REPO_URL);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.REPOSITORY_CREATE_NEW)
    public CreateRepositoryResponse create(CreateRepositoryRequest request) {
        String projectName = projectDao.getName(request.getProjectId());
        if (projectName == null) {
            throw new ValidationErrorsException("Project not found: " + request.getProjectId());
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.PROJECT_UPDATE_INSTANCE, projectName))) {
            throw new UnauthorizedException("The current user does not have permissions to update this project");
        }

        if (repositoryDao.exists(request.getName())) {
            throw new ValidationErrorsException("The repository already exists: " + request.getName());
        }

        String id = UUID.randomUUID().toString();
        repositoryDao.insert(request.getProjectId(), id, request.getName(), request.getUrl());
        return new CreateRepositoryResponse(id);
    }

    @Override
    public List<RepositoryEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2Field.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return repositoryDao.list(sortField, asc);
    }

    @Override
    @Validate
    public UpdateRepositoryResponse update(String id, UpdateRepositoryRequest request) {
        assertPermissions(id, Permissions.REPOSITORY_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified repository");
        repositoryDao.update(id, request.getUrl());
        return new UpdateRepositoryResponse();
    }

    @Override
    @Validate
    public DeleteRepositoryResponse delete(String id) {
        assertPermissions(id, Permissions.REPOSITORY_DELETE_INSTANCE,
                "The current user does not have permissions to delete the specified repository");
        repositoryDao.delete(id);
        return new DeleteRepositoryResponse();
    }

    private void assertPermissions(String id, String wildcard, String message) {
        String name = repositoryDao.getName(id);
        if (name == null) {
            throw new ValidationErrorsException("Repository not found: " + id);
        }

        Subject subject = SecurityUtils.getSubject();
        String perm = String.format(wildcard, name);
        if (!subject.isPermitted(perm)) {
            throw new SecurityException(message);
        }
    }
}
