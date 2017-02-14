package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.template.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.public_.tables.Templates.TEMPLATES;

@Named
public class TemplateResourceImpl implements TemplateResource, Resource {

    private final TemplateDao templateDao;
    private final Map<String, Field<?>> key2Field;

    @Inject
    public TemplateResourceImpl(TemplateDao templateDao) {
        this.templateDao = templateDao;

        this.key2Field = new HashMap<>();
        key2Field.put("name", TEMPLATES.TEMPLATE_NAME);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.TEMPLATE_CREATE_NEW)
    public CreateTemplateResponse create(String name, InputStream data) {
        if (templateDao.exists(name)) {
            throw new ValidationErrorsException("The template already exists: " + name);
        }

        templateDao.insert(name, data);
        return new CreateTemplateResponse();
    }

    @Override
    public List<TemplateEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2Field.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return templateDao.list(sortField, asc);
    }

    @Override
    @Validate
    public UpdateTemplateResponse update(String name, InputStream data) {
        if (!templateDao.exists(name)) {
            throw new ValidationErrorsException("Template not found: " + name);
        }
        templateDao.update(name, data);
        return new UpdateTemplateResponse();
    }

    @Override
    @Validate
    public DeleteTemplateResponse delete(String name) {
        if (!templateDao.exists(name)) {
            throw new ValidationErrorsException("Template not found: " + name);
        }

        templateDao.delete(name);
        return new DeleteTemplateResponse();
    }
}
