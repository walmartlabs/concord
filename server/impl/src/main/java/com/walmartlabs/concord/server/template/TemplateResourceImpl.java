package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.template.CreateTemplateResponse;
import com.walmartlabs.concord.server.api.template.DeleteTemplateResponse;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import com.walmartlabs.concord.server.api.template.UpdateTemplateResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.UUID;

@Named
public class TemplateResourceImpl implements TemplateResource, Resource {

    private final TemplateDao templateDao;

    @Inject
    public TemplateResourceImpl(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.TEMPLATE_CREATE_NEW)
    public CreateTemplateResponse create(String name, InputStream data) {
        if (templateDao.exists(name)) {
            throw new ValidationErrorsException("The template already exists: " + name);
        }

        String id = UUID.randomUUID().toString();
        templateDao.insert(id, name, data);
        return new CreateTemplateResponse(id);
    }

    @Override
    @Validate
    public UpdateTemplateResponse update(String id, InputStream data) {
        if (!templateDao.existsId(id)) {
            throw new WebApplicationException("Template not found: " + id, Status.NOT_FOUND);
        }

        templateDao.update(id, data);
        return new UpdateTemplateResponse();
    }

    @Override
    @Validate
    public DeleteTemplateResponse delete(String id) {
        if (!templateDao.existsId(id)) {
            throw new WebApplicationException("Template not found: " + id, Status.NOT_FOUND);
        }

        templateDao.delete(id);
        return new DeleteTemplateResponse();
    }
}
