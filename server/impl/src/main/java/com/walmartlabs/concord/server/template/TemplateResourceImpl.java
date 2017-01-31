package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.template.CreateTemplateResponse;
import com.walmartlabs.concord.server.api.template.DeleteTemplateResponse;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import com.walmartlabs.concord.server.api.template.UpdateTemplateResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
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
        String id = UUID.randomUUID().toString();
        templateDao.insert(id, name, data);
        return new CreateTemplateResponse(id);
    }

    @Override
    @Validate
    public UpdateTemplateResponse update(String id, InputStream data) {
        templateDao.update(id, data);
        return new UpdateTemplateResponse();
    }

    @Override
    @Validate
    public DeleteTemplateResponse delete(String id) {
        templateDao.delete(id);
        return new DeleteTemplateResponse();
    }
}
