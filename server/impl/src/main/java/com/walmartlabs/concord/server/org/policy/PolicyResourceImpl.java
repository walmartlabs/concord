package com.walmartlabs.concord.server.org.policy;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Named
public class PolicyResourceImpl implements PolicyResource, Resource {

    private final OrganizationManager orgManager;
    private final ProjectDao projectDao;
    private final ProjectAccessManager accessManager;
    private final PolicyDao policyDao;

    @Inject
    public PolicyResourceImpl(OrganizationManager orgManager, ProjectDao projectDao, ProjectAccessManager accessManager, PolicyDao policyDao) {
        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.accessManager = accessManager;
        this.policyDao = policyDao;
    }

    @Override
    public PolicyEntry get(String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return policyDao.get(org.getId(), null);
    }

    @Override
    public PolicyEntry get(String orgName, String projectName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new WebApplicationException("Project not found: " + projectName, Response.Status.NOT_FOUND);
        }

        ProjectEntry prj = accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);

        return policyDao.get(org.getId(), prj.getId());
    }
}
