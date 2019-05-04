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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.UserDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Policy", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/policy")
public class PolicyResource implements Resource {

    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final PolicyDao policyDao;
    private final UserDao userDao;
    private final AuditLog auditLog;

    @Inject
    public PolicyResource(OrganizationManager orgManager,
                          OrganizationDao orgDao,
                          ProjectDao projectDao,
                          UserDao userDao,
                          PolicyDao policyDao,
                          AuditLog auditLog) {

        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.projectDao = projectDao;
        this.policyDao = policyDao;
        this.userDao = userDao;
        this.auditLog = auditLog;
    }

    @GET
    @ApiOperation("Get an existing policy")
    @Path("/{policyName}")
    @Produces(MediaType.APPLICATION_JSON)
    public PolicyEntry get(@ApiParam @PathParam("policyName") @ConcordKey String policyName) {
        UUID id = policyDao.getId(policyName);
        if (id == null) {
            throw new ConcordApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        return policyDao.get(id);
    }

    @POST
    @ApiOperation("Create or update a policy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PolicyOperationResponse createOrUpdate(@ApiParam @Valid PolicyEntry entry) {
        assertAdmin();

        UUID id = entry.id();
        if (id == null && entry.name() != null) {
            id = policyDao.getId(entry.name());
        }

        if (id == null) {
            id = policyDao.insert(entry.name(), entry.parentId(), entry.rules());

            auditLog.add(AuditObject.POLICY, AuditAction.CREATE)
                    .field("id", id)
                    .field("parentId", entry.parentId())
                    .field("name", entry.name())
                    .log();

            return new PolicyOperationResponse(id, OperationResult.CREATED);
        } else {
            policyDao.update(id, entry.name(), entry.parentId(), entry.rules());

            auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                    .field("id", id)
                    .field("parentId", entry.parentId())
                    .field("name", entry.name())
                    .log();

            return new PolicyOperationResponse(id, OperationResult.UPDATED);
        }
    }

    @DELETE
    @ApiOperation("Delete an existing policy")
    @Path("/{policyName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("policyName") @ConcordKey String policyName) {
        assertAdmin();

        UUID id = policyDao.getId(policyName);
        if (id == null) {
            throw new ConcordApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        policyDao.delete(id);

        auditLog.add(AuditObject.POLICY, AuditAction.DELETE)
                .field("id", id)
                .field("name", policyName)
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @PUT
    @ApiOperation("Link an existing policy to an organization or a project")
    @Path("/{policyName}/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult link(@ApiParam @PathParam("policyName") @ConcordKey String policyName,
                                       @ApiParam @Valid PolicyLinkEntry entry) {

        assertAdmin();

        PolicyLink l = assertLink(policyName, entry.getOrgName(), entry.getProjectName(), entry.getUserName());
        policyDao.link(l.policyId, l.orgId, l.projectId, l.userId);

        auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                .field("id", l.policyId)
                .field("name", policyName)
                .field("link", l)
                .field("action", "link")
                .log();

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @DELETE
    @ApiOperation("Unlink an existing policy")
    @Path("/{policyName}/link")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult unlink(@ApiParam @PathParam("policyName") @ConcordKey String policyName,
                                         @ApiParam @QueryParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @QueryParam("projectName") @ConcordKey String projectName,
                                         @ApiParam @QueryParam("userName") @ConcordKey String userName) {

        assertAdmin();

        PolicyLink l = assertLink(policyName, orgName, projectName, userName);
        policyDao.unlink(l.policyId, l.orgId, l.projectId, l.userId);

        auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                .field("id", l.policyId)
                .field("name", policyName)
                .field("link", l)
                .field("action", "unlink")
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @GET
    @ApiOperation(value = "List policies, optionally filtering by organization and/or project links", responseContainer = "list", response = PolicyEntry.class)
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PolicyEntry> list(@ApiParam @QueryParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @QueryParam("projectName") @ConcordKey String projectName,
                                  @ApiParam @QueryParam("userName") @ConcordKey String userName) {

        if (orgName == null && projectName == null && userName == null) {
            return policyDao.list();
        }

        UUID orgId = null;
        if (orgName != null) {
            orgId = orgDao.getId(orgName);
            if (orgId == null) {
                throw new ConcordApplicationException("Organization not found: " + orgName, Status.BAD_REQUEST);
            }
        }

        UUID projectId = null;
        if (projectName != null) {
            if (orgId == null) {
                throw new ConcordApplicationException("Organization name is required", Status.BAD_REQUEST);
            }

            projectId = projectDao.getId(orgId, projectName);
            if (projectId == null) {
                throw new ConcordApplicationException("Project not found: " + projectName, Status.BAD_REQUEST);
            }
        }

        UUID userId = null;
        if (userName != null) {
            userId = assertUser(userName);
        }

        PolicyEntry e = policyDao.getLinked(orgId, projectId, userId);
        if (e == null) {
            return Collections.emptyList();
        }

        // TODO consider returning multiple entries?
        return Collections.singletonList(e);
    }

    private UUID assertProject(UUID orgId, String projectName) {
        UUID id = projectDao.getId(orgId, projectName);
        if (id == null) {
            throw new ConcordApplicationException("Project not found: " + projectName);
        }
        return id;
    }

    private UUID assertUser(String userName) {
        UUID id = userDao.getId(userName);
        if (id == null) {
            throw new ConcordApplicationException("User not found: " + userName, Status.BAD_REQUEST);
        }

        return id;
    }

    private PolicyLink assertLink(String policyName, String orgName, String projectName, String userName) {
        UUID policyId = policyDao.getId(policyName);
        if (policyId == null) {
            throw new ConcordApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        UUID orgId = null;
        if (orgName != null) {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            orgId = org.getId();
        }

        UUID projectId = null;
        if (projectName != null) {
            if (orgId != null) {
                projectId = assertProject(orgId, projectName);
            } else {
                throw new ConcordApplicationException("Organization name is required", Status.BAD_REQUEST);
            }
        }

        if (projectId != null) {
            // projectId is enough to make a proper reference
            orgId = null;
        }

        UUID userId = null;
        if (userName != null) {
            userId = assertUser(userName);
        }

        return new PolicyLink(policyId, orgId, projectId, userId);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }

    @JsonInclude(Include.NON_NULL)
    private static class PolicyLink implements Serializable {

        private final UUID policyId;
        private final UUID orgId;
        private final UUID projectId;
        private final UUID userId;

        private PolicyLink(UUID policyId, UUID orgId, UUID projectId, UUID userId) {
            this.policyId = policyId;
            this.orgId = orgId;
            this.projectId = projectId;
            this.userId = userId;
        }

        // needed for audit_log data

        public UUID getOrgId() {
            return orgId;
        }

        public UUID getProjectId() {
            return projectId;
        }

        public UUID getUserId() {
            return userId;
        }
    }
}
