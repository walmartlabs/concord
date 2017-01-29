package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.repository.CreateRepositoryRequest;
import com.walmartlabs.concord.server.api.repository.CreateRepositoryResponse;
import com.walmartlabs.concord.server.api.repository.RepositoryResource;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.api.security.Permissions;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
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
            throw new WebApplicationException("Project not found: " + request.getProjectId(), Status.BAD_REQUEST);
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.PROJECT_UPDATE_INSTANCE, projectName))) {
            throw new WebApplicationException("The current user does not have permissions to update this project", Status.FORBIDDEN);
        }

        String id = UUID.randomUUID().toString();
        repositoryDao.insert(request.getProjectId(), id, request.getName(), request.getUrl());
        return new CreateRepositoryResponse(id);
    }
}
