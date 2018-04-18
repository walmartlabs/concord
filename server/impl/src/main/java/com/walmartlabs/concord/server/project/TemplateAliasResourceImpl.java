package com.walmartlabs.concord.server.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.TemplateAliasEntry;
import com.walmartlabs.concord.server.api.project.TemplateAliasResource;
import com.walmartlabs.concord.server.api.project.TemplateAliasResponse;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
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
    public TemplateAliasResponse createOrUpdate(TemplateAliasEntry request) {
        assertAdmin();

        tx(tx -> {
            aliasDao.delete(request.getAlias());
            aliasDao.insert(request.getAlias(), request.getUrl());
        });

        return new TemplateAliasResponse();
    }

    @Override
    public List<TemplateAliasEntry> list() {
        return aliasDao.list();
    }

    @Override
    public TemplateAliasResponse delete(String alias) {
        assertAdmin();
        aliasDao.delete(alias);
        return new TemplateAliasResponse();
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
