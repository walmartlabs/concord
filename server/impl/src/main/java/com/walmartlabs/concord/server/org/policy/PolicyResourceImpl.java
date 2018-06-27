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
import com.walmartlabs.concord.server.api.GenericOperationResult;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyLinkEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyOperationResponse;
import com.walmartlabs.concord.server.api.org.policy.PolicyResource;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Named
public class PolicyResourceImpl implements PolicyResource, Resource {

    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final PolicyDao policyDao;
    private final AuditLog auditLog;

    @Inject
    public PolicyResourceImpl(OrganizationManager orgManager,
                              OrganizationDao orgDao,
                              ProjectDao projectDao,
                              PolicyDao policyDao, AuditLog auditLog) {

        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.projectDao = projectDao;
        this.policyDao = policyDao;
        this.auditLog = auditLog;
    }

    @Override
    public PolicyEntry get(String policyName) {
        UUID id = policyDao.getId(policyName);
        if (id == null) {
            throw new WebApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        return policyDao.get(id);
    }

    @Override
    public PolicyOperationResponse createOrUpdate(PolicyEntry entry) {
        assertAdmin();

        UUID id = entry.getId();
        if (id == null && entry.getName() != null) {
            id = policyDao.getId(entry.getName());
        }

        if (id == null) {
            id = policyDao.insert(entry.getName(), entry.getRules());

            auditLog.add(AuditObject.POLICY, AuditAction.CREATE)
                    .field("id", id)
                    .field("name", entry.getName())
                    .log();

            return new PolicyOperationResponse(id, OperationResult.CREATED);
        } else {
            policyDao.update(id, entry.getName(), entry.getRules());

            auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                    .field("id", id)
                    .field("name", entry.getName())
                    .log();

            return new PolicyOperationResponse(id, OperationResult.UPDATED);
        }
    }

    @Override
    public GenericOperationResult delete(String policyName) {
        assertAdmin();

        UUID id = policyDao.getId(policyName);
        if (id == null) {
            throw new WebApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
        }

        policyDao.delete(id);

        auditLog.add(AuditObject.POLICY, AuditAction.DELETE)
                .field("id", id)
                .field("name", policyName)
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @Override
    public GenericOperationResult link(String policyName, PolicyLinkEntry entry) {
        assertAdmin();

        PolicyLink l = assertLink(policyName, entry.getOrgName(), entry.getProjectName());
        policyDao.link(l.policyId, l.orgId, l.projectId);

        auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                .field("id", l.policyId)
                .field("name", policyName)
                .field("link", l)
                .field("action", "link")
                .log();

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @Override
    public GenericOperationResult unlink(String policyName, String orgName, String projectName) {
        assertAdmin();

        PolicyLink l = assertLink(policyName, orgName, projectName);
        policyDao.unlink(l.policyId, l.orgId, l.projectId);

        auditLog.add(AuditObject.POLICY, AuditAction.UPDATE)
                .field("id", l.policyId)
                .field("name", policyName)
                .field("link", l)
                .field("action", "unlink")
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @Override
    public List<PolicyEntry> list(String orgName, String projectName) {
        if (orgName == null && projectName == null) {
            return policyDao.list();
        }

        UUID orgId = null;
        if (orgName != null) {
            orgId = orgDao.getId(orgName);
            if (orgId == null) {
                throw new WebApplicationException("Organization not found: " + orgName, Status.BAD_REQUEST);
            }
        }

        UUID projectId = null;
        if (projectName != null) {
            if (orgId == null) {
                throw new WebApplicationException("Organization name is required", Status.BAD_REQUEST);
            }

            projectId = projectDao.getId(orgId, projectName);
            if (projectId == null) {
                throw new WebApplicationException("Project not found: " + projectName, Status.BAD_REQUEST);
            }
        }

        PolicyEntry e = policyDao.getLinked(orgId, projectId);
        if (e == null) {
            return Collections.emptyList();
        }

        // TODO consider returning multiple entries?
        return Collections.singletonList(e);
    }

    private UUID assertProject(UUID orgId, String projectName) {
        UUID id = projectDao.getId(orgId, projectName);
        if (id == null) {
            throw new WebApplicationException("Project not found: " + projectName);
        }
        return id;
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }

    private PolicyLink assertLink(String policyName, String orgName, String projectName) {
        UUID policyId = policyDao.getId(policyName);
        if (policyId == null) {
            throw new WebApplicationException("Policy not found: " + policyName, Status.NOT_FOUND);
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
                throw new WebApplicationException("Organization name is required", Status.BAD_REQUEST);
            }
        }

        if (projectId != null) {
            // projectId is enough to make a proper reference
            orgId = null;
        }

        return new PolicyLink(policyId, orgId, projectId);
    }

    @JsonInclude(Include.NON_NULL)
    private static class PolicyLink implements Serializable {

        private final UUID policyId;
        private final UUID orgId;
        private final UUID projectId;

        private PolicyLink(UUID policyId, UUID orgId, UUID projectId) {
            this.policyId = policyId;
            this.orgId = orgId;
            this.projectId = projectId;
        }

        // needed for audit_log data

        public UUID getOrgId() {
            return orgId;
        }

        public UUID getProjectId() {
            return projectId;
        }
    }
}
