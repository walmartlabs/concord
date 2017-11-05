package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.TemplateAliasEntry;
import com.walmartlabs.concord.server.api.project.TemplateAliasResource;
import com.walmartlabs.concord.server.api.project.TemplateAliasResponse;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jooq.Configuration;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class TemplateAliasResourceImpl extends AbstractDao implements TemplateAliasResource, Resource {

    private final TemplateAliasDao aliasDao;

    @Inject
    public TemplateAliasResourceImpl(Configuration cfg, TemplateAliasDao aliasDao) {
        super(cfg);
        this.aliasDao = aliasDao;
    }

    @Override
    @RequiresPermissions(Permissions.TEMPLATE_MANAGE)
    public TemplateAliasResponse createOrUpdate(TemplateAliasEntry request) {
        assertAdmin();

        tx(tx -> {
            aliasDao.delete(request.getAlias());
            aliasDao.insert(request.getAlias(), request.getUrl());
        });

        return new TemplateAliasResponse();
    }

    @Override
    @RequiresPermissions(Permissions.TEMPLATE_MANAGE)
    public List<TemplateAliasEntry> list() {
        return aliasDao.list();
    }

    @Override
    @RequiresPermissions(Permissions.TEMPLATE_MANAGE)
    public TemplateAliasResponse delete(String alias) {
        assertAdmin();
        aliasDao.delete(alias);
        return new TemplateAliasResponse();
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Not authorized");
        }
    }
}
