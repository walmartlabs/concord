package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
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
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

public class ProjectManager {

    private final OrganizationManager orgManager;
    private final PolicyManager policyManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final SecretDao secretDao;
    private final KvDao kvDao;
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
                          KvDao kvDao,
                          ProjectRepositoryManager projectRepositoryManager,
                          ProjectAccessManager accessManager,
                          AuditLog auditLog,
                          EncryptedProjectValueManager encryptedValueManager,
                          UserManager userManager) {

        this.policyManager = policyManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.kvDao = kvDao;
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
        entry = normalize(entry);

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = entry.getId();
        if (projectId == null) {
            assertName(entry);
            projectId = projectDao.getId(org.getId(), entry.getName());
        }

        if (projectId == null) {
            projectId = insert(org.getId(), org.getName(), entry);
            return ProjectOperationResult.builder()
                    .result(OperationResult.CREATED)
                    .projectId(projectId)
                    .build();
        } else {
            update(projectId, entry);
            return ProjectOperationResult.builder()
                    .result(OperationResult.UPDATED)
                    .projectId(projectId)
                    .build();
        }
    }

    public ProjectKvCapacity getKvCapacity(String orgName, String projectName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        ProjectEntry project = accessManager.assertAccess(org.getId(), null, projectName, ResourceAccessLevel.READER, false);

        long currentSize = kvDao.count(project.getId());

        PolicyEngine policy = policyManager.get(org.getId(), project.getId(), UserPrincipal.assertCurrent().getId());
        Integer maxEntries = null;
        if (policy != null) {
            maxEntries = policy.getKvPolicy().getMaxEntries();
        }

        return ProjectKvCapacity.builder()
                .size(currentSize)
                .maxSize(maxEntries)
                .build();
    }

    private UUID insert(UUID orgId, String orgName, ProjectEntry entry) {
        UserEntry owner = getOwner(entry.getOwner(), UserPrincipal.assertCurrent().getUser());

        policyManager.checkEntity(orgId, null, EntityType.PROJECT, EntityAction.CREATE, owner, PolicyUtils.projectToMap(orgId, orgName, entry));

        byte[] encryptedKey = encryptedValueManager.createEncryptedSecretKey();

        RawPayloadMode rawPayloadMode;
        if (entry.getRawPayloadMode() == null) {
            rawPayloadMode = RawPayloadMode.ORG_MEMBERS;
        } else {
            rawPayloadMode = entry.getRawPayloadMode();
        }

        Map<String, ProcessDefinition> processDefinitions = loadProcessDefinitions(orgId, null, entry);

        UUID id = projectDao.txResult(tx -> insert(tx, orgId, owner, entry, rawPayloadMode, encryptedKey, processDefinitions));

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

    private UUID insert(DSLContext tx, UUID orgId, UserEntry owner, ProjectEntry entry, RawPayloadMode rawPayloadMode, byte[] encryptedKey, Map<String, ProcessDefinition> processDefinitions) {
        UUID id = projectDao.insert(tx, orgId, entry.getName(), entry.getDescription(), owner.getId(), entry.getCfg(),
                entry.getVisibility(), rawPayloadMode, encryptedKey, entry.getMeta(), entry.getOutVariablesMode(), entry.getProcessExecMode());

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        if (repos != null) {
            projectRepositoryManager.replace(tx, orgId, id, repos.values(), processDefinitions);
        }
        return id;
    }

    private Map<String, ProcessDefinition> loadProcessDefinitions(UUID orgId, UUID projectId, ProjectEntry projectEntry) {
        if (projectEntry.getRepositories() == null || projectEntry.getRepositories().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ProcessDefinition> result = new HashMap<>();
        for (Map.Entry<String, RepositoryEntry> e : projectEntry.getRepositories().entrySet()) {
            if (e.getValue().isDisabled()) {
                continue;
            }
            ProcessDefinition processDefinition = projectRepositoryManager.processDefinition(orgId, projectId, e.getValue());
            result.put(e.getKey(), processDefinition);
        }
        return result;
    }

    private void update(UUID projectId, ProjectEntry entry) {
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

        RawPayloadMode rawPayloadMode;
        if (entry.getRawPayloadMode() == null) {
            rawPayloadMode = RawPayloadMode.ORG_MEMBERS;
        } else {
            rawPayloadMode = entry.getRawPayloadMode();
        }

        Map<String, ProcessDefinition> processDefinitions = loadProcessDefinitions(e.getOrgId(), projectId, entry);

        ProjectEntry newEntry = projectDao.txResult(tx -> update(tx, orgIdUpdate, orgId, projectId, entry, updatedOwnerId, rawPayloadMode, processDefinitions));

        Map<String, Object> changes = DiffUtils.compare(prevEntry, newEntry);
        addAuditLog(
                AuditAction.UPDATE,
                prevEntry.getOrgId(),
                prevEntry.getOrgName(),
                prevEntry.getId(),
                prevEntry.getName(),
                changes);
    }

    private ProjectEntry update(DSLContext tx, UUID orgIdUpdate, UUID orgIdPrev, UUID projectId, ProjectEntry entry, UUID updatedOwnerId, RawPayloadMode rawPayloadMode, Map<String, ProcessDefinition> processDefinitions) {
        if (!orgIdUpdate.equals(orgIdPrev)) {
            secretDao.updateProjectScopeByProjectId(tx, orgIdPrev, projectId, null);
            repositoryDao.clearSecretMappingByProjectId(tx, projectId);
        }

        projectDao.update(tx, orgIdUpdate, projectId, entry.getVisibility(), entry.getName(),
                entry.getDescription(), entry.getCfg(), rawPayloadMode, updatedOwnerId, entry.getMeta(), entry.getOutVariablesMode(),
                entry.getProcessExecMode());

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        if (repos != null) {
            projectRepositoryManager.replace(tx, orgIdUpdate, projectId, entry.getRepositories().values(), processDefinitions);
        }

        return projectDao.get(tx, projectId);
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
        if (repos == null) {
            return e;
        }

        Map<String, RepositoryEntry> m = new HashMap<>(repos);

        repos.forEach((k, v) -> {
            if (v.getName() == null) {
                RepositoryEntry r = new RepositoryEntry(k, v);
                m.put(k, r);
            }
        });

        return ProjectEntry.replace(e, m);
    }

    private static void assertName(ProjectEntry p) {
        String s = p.getName();
        if (s == null || s.trim().isEmpty()) {
            throw new ValidationErrorsException("'name' is required");
        }
    }
}
