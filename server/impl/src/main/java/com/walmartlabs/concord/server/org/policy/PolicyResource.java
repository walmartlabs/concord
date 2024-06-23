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
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.shiro.authz.AuthorizationException;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/policy")
@Tag(name = "Policy")
public class PolicyResource implements Resource {

    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final PolicyManager policyManager;
    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public PolicyResource(OrganizationManager orgManager,
                          OrganizationDao orgDao,
                          ProjectDao projectDao,
                          UserManager userManager,
                          PolicyManager policyManager,
                          AuditLog auditLog) {

        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.projectDao = projectDao;
        this.policyManager = policyManager;
        this.userManager = userManager;
        this.auditLog = auditLog;
    }

    @GET
    @Path("/{policyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get an existing policy", operationId = "getPolicy")
    public PolicyEntry get(@PathParam("policyName") @ConcordKey String policyName) {
        assertAdmin();
        PolicyEntry p = policyManager.get(policyName);
        if (p == null) {
            throw new ConcordApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        return p;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create or update a policy", operationId = "createOrUpdatePolicy")
    public PolicyOperationResponse createOrUpdate(@Valid PolicyEntry entry) {
        assertAdmin();

        UUID id = entry.id();
        if (id == null && entry.name() != null) {
            id = policyManager.getId(entry.name());
        }

        if (id == null) {
            id = policyManager.insert(entry.name(), entry.parentId(), entry.rules());

            auditLog.add(AuditObject.POLICY, AuditAction.CREATE)
                    .field("policyId", id)
                    .field("parentId", entry.parentId())
                    .field("name", entry.name())
                    .log();

            return new PolicyOperationResponse(id, OperationResult.CREATED);
        } else {
            policyManager.update(id, entry.name(), entry.parentId(), entry.rules());

            auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                    .field("policyId", id)
                    .field("parentId", entry.parentId())
                    .field("name", entry.name())
                    .log();

            return new PolicyOperationResponse(id, OperationResult.UPDATED);
        }
    }

    @DELETE
    @Path("/{policyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete an existing policy", operationId = "deletePolicy")
    public GenericOperationResult delete(@PathParam("policyName") @ConcordKey String policyName) {
        assertAdmin();

        UUID id = policyManager.getId(policyName);
        if (id == null) {
            throw new ConcordApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        policyManager.delete(id);

        auditLog.add(AuditObject.POLICY, AuditAction.DELETE)
                .field("policyId", id)
                .field("name", policyName)
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @PUT
    @Path("/{policyName}/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Link an existing policy to an organization, a project or user", operationId = "linkPolicy")
    public GenericOperationResult link(@PathParam("policyName") @ConcordKey String policyName,
                                       @Valid PolicyLinkEntry entry) {

        assertAdmin();

        // TODO: add user type into request
        UserType userType = UserPrincipal.assertCurrent().getType();
        PolicyLink l = assertLink(policyName, entry.getOrgName(), entry.getProjectName(), entry.getUserName(), entry.getUserDomain(), userType);
        policyManager.link(l.policyId, l.orgId, l.projectId, l.userId);

        auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                .field("policyId", l.policyId)
                .field("name", policyName)
                .field("link", l)
                .field("action", "link")
                .log();

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @DELETE
    @Path("/{policyName}/link")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Unlink an existing policy", operationId = "unlinkPolicy")
    public GenericOperationResult unlink(@PathParam("policyName") @ConcordKey String policyName,
                                         @QueryParam("orgName") @ConcordKey String orgName,
                                         @QueryParam("projectName") @ConcordKey String projectName,
                                         @QueryParam("userName") @ConcordKey String userName,
                                         @QueryParam("userDomain") String domain,
                                         @QueryParam("userType") UserType userType) {

        assertAdmin();

        if (userType == null) {
            userType = UserPrincipal.assertCurrent().getType();
        }
        PolicyLink l = assertLink(policyName, orgName, projectName, userName, domain, userType);
        policyManager.unlink(l.policyId, l.orgId, l.projectId, l.userId);

        auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                .field("policyId", l.policyId)
                .field("name", policyName)
                .field("link", l)
                .field("action", "unlink")
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List policies, optionally filtering by organization, project and/or user links", operationId = "listPolicies")
    public List<PolicyEntry> list(@QueryParam("orgName") @ConcordKey String orgName,
                                  @QueryParam("projectName") @ConcordKey String projectName,
                                  @QueryParam("userName") @ConcordKey String userName,
                                  @QueryParam("userDomain") String userDomain,
                                  @QueryParam("userType") UserType userType) {

        assertAdmin();

        if (orgName == null && projectName == null && userName == null) {
            return policyManager.list();
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
            if (userType == null) {
                userType = UserPrincipal.assertCurrent().getType();
            }
            userId = assertUser(userName, userDomain, userType);
        }

        PolicyEntry e = policyManager.getLinked(orgId, projectId, userId);
        if (e == null) {
            return Collections.emptyList();
        }

        // TODO consider returning multiple entries?
        return Collections.singletonList(e);
    }

    @POST
    @Path("/refresh")
    @Operation(description = "Refresh policy", operationId = "refreshPolicy")
    public void refresh() {
        if (!Roles.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to refresh polices");
        }

        policyManager.refresh();
    }

    private UUID assertProject(UUID orgId, String projectName) {
        UUID id = projectDao.getId(orgId, projectName);
        if (id == null) {
            throw new ConcordApplicationException("Project not found: " + projectName);
        }
        return id;
    }

    private UUID assertUser(String userName, String doamin, UserType userType) {
        return userManager.getId(userName, doamin, userType)
                .orElseThrow(() -> new ConcordApplicationException("User '" + userName + "' with domain '" + doamin + "' (" + userType + ") not found", Status.BAD_REQUEST));
    }

    private PolicyLink assertLink(String policyName, String orgName, String projectName, String userName, String domain, UserType userType) {
        UUID policyId = policyManager.getId(policyName);
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
        if (userName != null && userType != null) {
            userId = assertUser(userName, domain, userType);
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

        private static final long serialVersionUID = 1L;

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
