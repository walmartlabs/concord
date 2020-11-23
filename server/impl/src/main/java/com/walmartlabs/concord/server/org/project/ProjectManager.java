package com.walmartlabs.concord.server.org.project;

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

import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.jooq.enums.RawPayloadMode;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.DSLContext;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

@Named
public class ProjectManager {

    private final OrganizationManager orgManager;
    private final PolicyManager policyManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final SecretDao secretDao;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final ProjectAccessManager accessManager;
    private final AuditLog auditLog;
    private final EncryptedProjectValueManager encryptedValueManager;
    private final UserManager userManager;

    @Inject
    public ProjectManager(OrganizationManager orgManager,
                          PolicyManager policyManager,
                          ProjectDao projectDao,
                          RepositoryDao repositoryDao,
                          SecretDao secretDao,
                          ProjectRepositoryManager projectRepositoryManager,
                          ProjectAccessManager accessManager,
                          AuditLog auditLog,
                          EncryptedProjectValueManager encryptedValueManager,
                          UserManager userManager) {

        this.policyManager = policyManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.projectRepositoryManager = projectRepositoryManager;
        this.accessManager = accessManager;
        this.auditLog = auditLog;
        this.encryptedValueManager = encryptedValueManager;
        this.userManager = userManager;
        this.orgManager = orgManager;
    }

    public ProjectEntry get(String orgName, String projectName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Response.Status.NOT_FOUND);
        }

        return get(projectId);
    }

    public ProjectEntry get(UUID projectId) {
        return projectDao.txResult(tx -> get(tx, projectId));
    }

    public ProjectEntry get(DSLContext tx, UUID projectId) {
        return accessManager.assertAccess(tx, projectId, ResourceAccessLevel.READER, false);
    }

    /**
     * Creates a new project or updates an existing one.
     * <p/>
     * To update an existing project, a {@link ProjectEntry#getId()}
     * value must be provided.
     * <p/>
     * When updating an existing project, only non-null properties are updated.
     * E.g. if you wish to update only the project's visibility, set only the ID
     * and the visibility values.
     */
    public ProjectOperationResult createOrUpdate(String orgName, ProjectEntry entry) {
        return projectDao.txResult(tx -> createOrUpdate(tx, orgName, entry));
    }

    public ProjectOperationResult createOrUpdate(DSLContext tx, String orgName, ProjectEntry entry) {
        entry = normalize(entry);

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = entry.getId();
        if (projectId == null) {
            assertName(entry);
            projectId = projectDao.getId(tx, org.getId(), entry.getName());
        }

        if (projectId == null) {
            projectId = insert(tx, org.getId(), org.getName(), entry);
            return ProjectOperationResult.builder()
                    .result(OperationResult.CREATED)
                    .projectId(projectId)
                    .build();
        } else {
            update(tx, projectId, entry);
            return ProjectOperationResult.builder()
                    .result(OperationResult.UPDATED)
                    .projectId(projectId)
                    .build();
        }
    }

    public ProjectOperationResult createOrGet(UUID orgId, ProjectEntry entry) {
        return projectDao.txResult(tx -> createOrGet(tx, orgId, entry));
    }

    public ProjectOperationResult createOrGet(DSLContext tx, UUID orgId, ProjectEntry entry) {
        entry = normalize(entry);

        OrganizationEntry org = orgManager.assertAccess(tx, orgId, true);

        UUID projectId = projectDao.getId(tx, org.getId(), entry.getName());
        if (projectId == null) {
            projectId = insert(tx, org.getId(), org.getName(), entry);
            return ProjectOperationResult.builder()
                    .result(OperationResult.CREATED)
                    .projectId(projectId)
                    .build();
        }

        return ProjectOperationResult.builder()
                .result(OperationResult.ALREADY_EXISTS)
                .projectId(projectId)
                .build();
    }

    private UUID insert(DSLContext tx, UUID orgId, String orgName, ProjectEntry entry) {
        UserEntry owner = getOwner(entry.getOwner(), UserPrincipal.assertCurrent().getUser());

        policyManager.checkEntity(orgId, null, EntityType.PROJECT, EntityAction.CREATE, owner, PolicyUtils.projectToMap(orgId, orgName, entry));

        byte[] encryptedKey = encryptedValueManager.createEncryptedSecretKey();

        RawPayloadMode rawPayloadMode = entry.getRawPayloadMode();
        if (rawPayloadMode == null && entry.getAcceptsRawPayload() != null && entry.getAcceptsRawPayload()) {
            rawPayloadMode = RawPayloadMode.ORG_MEMBERS;
        }

        UUID id = projectDao.insert(tx, orgId, entry.getName(), entry.getDescription(), owner.getId(), entry.getCfg(),
                entry.getVisibility(), rawPayloadMode, encryptedKey, entry.getMeta(), entry.getOutVariablesMode());

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        if (repos != null) {
            repos.forEach((k, v) -> projectRepositoryManager.insert(tx, orgId, orgName, id, entry.getName(), v, false));
        }

        Map<String, Object> changes = DiffUtils.compare(null, entry);
        addAuditLog(
                AuditAction.CREATE,
                orgId,
                orgName,
                id,
                entry.getName(),
                changes);

        return id;
    }

    private void update(DSLContext tx, UUID projectId, ProjectEntry entry) {
        ProjectEntry e = projectDao.get(projectId);
        if (e == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        UserEntry owner = getOwner(entry.getOwner(), null);
        policyManager.checkEntity(e.getOrgId(), projectId, EntityType.PROJECT, EntityAction.UPDATE, owner, PolicyUtils.projectToMap(e.getOrgId(), e.getOrgName(), entry));

        UUID currentOwnerId = e.getOwner() != null ? e.getOwner().id() : null;
        UUID updatedOwnerId = owner != null ? owner.getId() : null;

        ResourceAccessLevel level = ResourceAccessLevel.WRITER;
        if (updatedOwnerId != null && !updatedOwnerId.equals(currentOwnerId)) {
            level = ResourceAccessLevel.OWNER;
        }

        ProjectEntry prevEntry = accessManager.assertAccess(projectId, level, true);
        UUID orgId = prevEntry.getOrgId();

        OrganizationEntry organizationEntry = null;

        if (entry.getOrgId() != null) {
            organizationEntry = orgManager.assertAccess(entry.getOrgId(), true);
        } else if (entry.getOrgName() != null) {
            organizationEntry = orgManager.assertAccess(entry.getOrgName(), true);
        }

        UUID orgIdUpdate = organizationEntry != null ? organizationEntry.getId() : orgId;

        RawPayloadMode rawPayloadMode = entry.getRawPayloadMode();
        if (rawPayloadMode == null && entry.getAcceptsRawPayload() != null && entry.getAcceptsRawPayload()) {
            rawPayloadMode = RawPayloadMode.ORG_MEMBERS;
        }

        if (!orgIdUpdate.equals(orgId)) {
            secretDao.updateProjectScopeByProjectId(tx, orgId, projectId, null);
            repositoryDao.clearSecretMappingByProjectId(tx, projectId);
        }

        projectDao.update(tx, orgIdUpdate, projectId, entry.getVisibility(), entry.getName(),
                entry.getDescription(), entry.getCfg(), rawPayloadMode, updatedOwnerId, entry.getMeta(), entry.getOutVariablesMode());

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        if (repos != null) {
            repositoryDao.deleteAll(tx, projectId);
            repos.forEach((k, v) -> projectRepositoryManager.insert(tx, orgId, prevEntry.getOrgName(), projectId, prevEntry.getName(), v, false));
        }

        ProjectEntry newEntry = projectDao.get(tx, projectId);

        Map<String, Object> changes = DiffUtils.compare(prevEntry, newEntry);
        addAuditLog(
                AuditAction.UPDATE,
                prevEntry.getOrgId(),
                prevEntry.getOrgName(),
                prevEntry.getId(),
                prevEntry.getName(),
                changes);
    }

    public void delete(UUID projectId) {
        ProjectEntry e = accessManager.assertAccess(projectId, ResourceAccessLevel.OWNER, true);

        projectDao.delete(projectId);

        addAuditLog(
                AuditAction.DELETE,
                e.getOrgId(),
                e.getOrgName(),
                e.getId(),
                e.getName(),
                null);
    }

    public List<ProjectEntry> list(UUID orgId, int offset, int limit, String filter) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        if (Roles.isAdmin() || Roles.isGlobalReader() || Roles.isGlobalWriter()) {
            // admins or "global readers" can see any project, so we shouldn't filter projects by user
            userId = null;
        }

        return projectDao.list(orgId, userId, PROJECTS.PROJECT_NAME, true, offset, limit, filter);
    }

    public UserEntry getOwner(EntityOwner owner, UserEntry defaultOwner) {
        if (owner == null) {
            return defaultOwner;
        }

        if (owner.id() != null) {
            return userManager.get(owner.id())
                    .orElseThrow(() -> new ValidationErrorsException("User not found: " + owner.id()));
        }

        if (owner.username() != null) {
            return userManager.get(owner.username(), owner.userDomain(), UserType.LDAP)
                    .orElseThrow(() -> new ConcordApplicationException("User not found: " + owner.username()));
        }

        return defaultOwner;
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, String orgName, UUID projectId, String projectName, Map<String, Object> changes) {
        auditLog.add(AuditObject.PROJECT, auditAction)
                .field("orgId", orgId)
                .field("orgName", orgName)
                .field("projectId", projectId)
                .field("name", projectName)
                .field("changes", changes)
                .log();
    }

    private static ProjectEntry normalize(ProjectEntry e) {
        Map<String, RepositoryEntry> repos = e.getRepositories();
        if (repos != null) {
            Map<String, RepositoryEntry> m = new HashMap<>(repos);

            repos.forEach((k, v) -> {
                if (v.getName() == null) {
                    RepositoryEntry r = new RepositoryEntry(k, v);
                    m.put(k, r);
                }
            });

            e = ProjectEntry.replace(e, m);
        }

        return e;
    }

    private static void assertName(ProjectEntry p) {
        String s = p.getName();
        if (s == null || s.trim().isEmpty()) {
            throw new ValidationErrorsException("'name' is required");
        }
    }
}
