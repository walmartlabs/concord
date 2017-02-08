package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.template.TemplateDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.PathParam;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;

@Named
public class ProjectResourceImpl implements ProjectResource, Resource {

    private final ProjectDao projectDao;
    private final TemplateDao templateDao;

    private final Map<String, Field<?>> key2Field;

    @Inject
    public ProjectResourceImpl(ProjectDao projectDao, TemplateDao templateDao) {
        this.projectDao = projectDao;
        this.templateDao = templateDao;

        this.key2Field = new HashMap<>();
        key2Field.put("projectId", PROJECTS.PROJECT_ID);
        key2Field.put("name", PROJECTS.PROJECT_NAME);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.PROJECT_CREATE_NEW)
    public CreateProjectResponse create(CreateProjectRequest request) {
        if (projectDao.exists(request.getName())) {
            throw new ValidationErrorsException("Project already exists: " + request.getName());
        }

        String[] templateIds = getTemplateIds(request.getTemplates());
        String id = UUID.randomUUID().toString();
        projectDao.insert(id, request.getName(), templateIds);

        return new CreateProjectResponse(id);
    }

    @Override
    @Validate
    public ProjectEntry get(@PathParam("id") @ConcordId String id) {
        assertPermissions(id, Permissions.PROJECT_READ_INSTANCE,
                "The current user does not have permissions to read the specified project");
        return projectDao.get(id);
    }

    @Override
    @Validate
    public List<ProjectEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2Field.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return projectDao.list(sortField, asc);
    }

    @Override
    @Validate
    public UpdateProjectResponse update(String id, UpdateProjectRequest request) {
        assertPermissions(id, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update the specified project");

        String[] templateIds = getTemplateIds(request.getTemplates());
        projectDao.update(id, templateIds);
        return new UpdateProjectResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(String id) {
        assertPermissions(id, Permissions.PROJECT_DELETE_INSTANCE,
                "The current user does not have permissions to delete the specified project");

        projectDao.delete(id);
        return new DeleteProjectResponse();
    }

    private String[] getTemplateIds(String[] templateNames) {
        String[] templateIds = null;
        if (templateNames != null) {
            templateIds = Arrays.stream(templateNames).map(n -> {
                assertTemplatePermissions(n);

                String id = templateDao.getId(n);
                if (id == null) {
                    throw new ValidationErrorsException("Unknown template: " + n);
                }
                return id;
            }).toArray(String[]::new);
        }
        return templateIds;
    }

    private void assertPermissions(String projectId, String wildcard, String message) {
        String name = projectDao.getName(projectId);
        if (name == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        Subject subject = SecurityUtils.getSubject();
        String perm = String.format(wildcard, name);
        if (!subject.isPermitted(perm)) {
            throw new SecurityException(message);
        }
    }

    private static void assertTemplatePermissions(String name) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.TEMPLATE_USE_INSTANCE, name))) {
            throw new SecurityException("The current user does not have permissions to use the template: " + name);
        }
    }
}
