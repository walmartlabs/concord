package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.repository.CreateRepositoryRequest;
import com.walmartlabs.concord.server.api.repository.CreateRepositoryResponse;
import com.walmartlabs.concord.server.api.repository.RepositoryResource;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.project.ProjectDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class RepositoryResourceImpl implements RepositoryResource, Resource {

    private final RepositoryDao repositoryDao;
    private final ProjectDao projectDao;

    @Inject
    public RepositoryResourceImpl(RepositoryDao repositoryDao, ProjectDao projectDao) {
        this.repositoryDao = repositoryDao;
        this.projectDao = projectDao;
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
}
