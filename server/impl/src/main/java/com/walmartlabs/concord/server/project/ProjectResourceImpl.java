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

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
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
        String[] templateIds = getTemplateIds(request.getTemplates());
        String id = UUID.randomUUID().toString();
        projectDao.insert(id, request.getName(), templateIds);
        return new CreateProjectResponse(id);
    }

    @Override
    @Validate
    public List<ProjectEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2Field.get(sortBy);
        if (sortField == null) {
            throw new WebApplicationException("Unknown sort field: " + sortBy, Status.BAD_REQUEST);
        }
        return projectDao.list(sortField, asc);
    }

    @Override
    @Validate
    public UpdateProjectResponse update(@PathParam("id") @ConcordId String id, UpdateProjectRequest request) {
        assertPermissions(id, Permissions.PROJECT_UPDATE_INSTANCE,
                "The current user does not have permissions to update this project");

        String[] templateIds = getTemplateIds(request.getTemplates());
        projectDao.update(id, templateIds);
        return new UpdateProjectResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(@PathParam("id") @ConcordId String id) {
        assertPermissions(id, Permissions.PROJECT_DELETE_INSTANCE,
                "The current user does not have permissions to delete this project");

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
                    throw new WebApplicationException("Unknown template: " + n, Status.BAD_REQUEST);
                }
                return id;
            }).toArray(String[]::new);
        }
        return templateIds;
    }

    private void assertPermissions(String projectId, String wildcard, String message) {
        String name = projectDao.getName(projectId);
        if (name == null) {
            throw new WebApplicationException("Project not found: " + projectId, Status.NOT_FOUND);
        }

        Subject subject = SecurityUtils.getSubject();
        String perm = String.format(wildcard, name);
        if (!subject.isPermitted(perm)) {
            throw new WebApplicationException(message, Status.FORBIDDEN);
        }
    }

    private static void assertTemplatePermissions(String name) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(Permissions.TEMPLATE_USE_INSTANCE, name))) {
            throw new WebApplicationException("The current user does not have permissions to use the template: " + name, Status.FORBIDDEN);
        }
    }
}
