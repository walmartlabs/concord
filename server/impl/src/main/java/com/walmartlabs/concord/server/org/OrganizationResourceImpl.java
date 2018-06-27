package com.walmartlabs.concord.server.org;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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


import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.CreateOrganizationResponse;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

@Named
public class OrganizationResourceImpl implements OrganizationResource, Resource {

    private final OrganizationDao orgDao;
    private final OrganizationManager orgManager;

    @Inject
    public OrganizationResourceImpl(OrganizationDao orgDao, OrganizationManager orgManager) {
        this.orgDao = orgDao;
        this.orgManager = orgManager;
    }

    @Override
    public CreateOrganizationResponse createOrUpdate(OrganizationEntry entry) {
        UUID orgId = entry.getId();
        if (orgId == null) {
            orgId = orgManager.create(entry);
            return new CreateOrganizationResponse(orgId, OperationResult.CREATED);
        } else {
            orgManager.update(entry);
            return new CreateOrganizationResponse(orgId, OperationResult.UPDATED);
        }
    }

    @Override
    public OrganizationEntry get(String orgName) {
        return orgDao.getByName(orgName);
    }

    @Override
    public List<OrganizationEntry> list(boolean onlyCurrent) {
        UUID userId = null;

        if (onlyCurrent) {
            UserPrincipal p = UserPrincipal.assertCurrent();
            if (!p.isAdmin() && !p.isGlobalReader() && !p.isGlobalWriter()) {
                userId = p.getId();
            }
        }

        return orgDao.list(userId);
    }
}
