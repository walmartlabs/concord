package com.walmartlabs.concord.server.org.policy;

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

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.ProjectManager;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Named
@Singleton
@Api(value = "PolicyCheck", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1")
public class PolicyCheckResource implements Resource {

    private final OrganizationManager orgManager;
    private final ProjectManager projectManager;
    private final PolicyManager policyManager;

    @Inject
    public PolicyCheckResource(OrganizationManager orgManager,
                               ProjectManager projectManager,
                               PolicyManager policyManager) {

        this.orgManager = orgManager;
        this.projectManager = projectManager;
        this.policyManager = policyManager;
    }

    @GET
    @ApiOperation("Check for new entity creation")
    @Path("/{entityType}/canCreate")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public boolean canCreate(@ApiParam @PathParam("entityType") EntityType type,
                             @ApiParam @QueryParam("orgName") String orgName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        return isCreateEnabled(org.getId(), orgName, type);
    }

    private boolean isCreateEnabled(UUID orgId, String orgName, EntityType entity) {
        try {
            switch (entity) {
                case PROJECT: {
                    ProjectEntry entry = new ProjectEntry(entity.toString());
                    UserEntry owner = projectManager.getOwner(entry.getOwner(), UserPrincipal.assertCurrent().getUser());
                    policyManager.checkEntity(orgId, null, EntityType.PROJECT, EntityAction.CREATE, owner, PolicyUtils.projectToMap(orgId, orgName, entry));
                    break;
                }
                case SECRET: {
                    UserEntry owner = UserPrincipal.assertCurrent().getUser();
                    policyManager.checkEntity(orgId, null, EntityType.SECRET, EntityAction.CREATE, owner, PolicyUtils.secretToMap(orgId, null, null, null, null));
                    break;
                }
                default: {
                    // nothing to do, the implementation supports only projects and secrets

                    // when the UI supports creation of other types of entities then this code
                    // can be extended to support it
                }
            }

            return true;
        } catch (ValidationErrorsException v) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
