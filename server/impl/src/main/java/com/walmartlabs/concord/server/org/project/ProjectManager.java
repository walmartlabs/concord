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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

@Named
public class ProjectManager {

    private final PolicyManager policyManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final ProjectAccessManager accessManager;
    private final SecretManager secretManager;
    private final AuditLog auditLog;
    private final EncryptedProjectValueManager encryptedValueManager;
    private final UserManager userManager;

    @Inject
    public ProjectManager(PolicyManager policyManager,
                          ProjectDao projectDao,
                          RepositoryDao repositoryDao,
                          ProjectRepositoryManager projectRepositoryManager,
                          ProjectAccessManager accessManager,
                          SecretManager secretManager,
                          AuditLog auditLog,
                          EncryptedProjectValueManager encryptedValueManager,
                          UserManager userManager) {

        this.policyManager = policyManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.projectRepositoryManager = projectRepositoryManager;
        this.accessManager = accessManager;
        this.secretManager = secretManager;
        this.auditLog = auditLog;
        this.encryptedValueManager = encryptedValueManager;
        this.userManager = userManager;
    }

    public ProjectEntry get(UUID projectId) {
        return accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);
    }

    public UUID insert(UUID orgId, String orgName, ProjectEntry entry) {
        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        UserEntry owner = getOwner(entry.getOwner(), UserPrincipal.assertCurrent().getUser());

        policyManager.checkEntity(orgId, null, EntityType.PROJECT, EntityAction.CREATE, owner, PolicyUtils.toMap(orgId, orgName, entry));

        UUID id = projectDao.txResult(tx -> {
            boolean acceptsRawPayload = entry.getAcceptsRawPayload() != null ? entry.getAcceptsRawPayload() : false;
            byte[] encryptedKey = encryptedValueManager.createEncryptedSecretKey();

            UUID pId = projectDao.insert(tx, orgId, entry.getName(), entry.getDescription(), owner.getId(), entry.getCfg(),
                    entry.getVisibility(), acceptsRawPayload, encryptedKey, entry.getMeta());

            if (repos != null) {
                repos.forEach((k, v) -> projectRepositoryManager.insert(tx, orgId, orgName, pId, entry.getName(), v, false));
            }

            return pId;
        });

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

    public void update(UUID projectId, ProjectEntry entry) {
        ProjectEntry e = projectDao.get(projectId);
        if (e == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        UserEntry owner = getOwner(entry.getOwner(), null);
        policyManager.checkEntity(e.getOrgId(), projectId, EntityType.PROJECT, EntityAction.UPDATE, owner, PolicyUtils.toMap(e.getOrgId(), e.getOrgName(), entry));

        UUID currentOwnerId = e.getOwner() != null ? e.getOwner().id() : null;
        UUID updatedOwnerId = owner != null ? owner.getId() : null;

        ResourceAccessLevel level = ResourceAccessLevel.WRITER;
        if (updatedOwnerId != null && !updatedOwnerId.equals(currentOwnerId)) {
            level = ResourceAccessLevel.OWNER;
        }

        ProjectEntry prevEntry = accessManager.assertProjectAccess(projectId, level, true);
        UUID orgId = prevEntry.getOrgId();

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        projectDao.tx(tx -> {
            projectDao.update(tx, orgId, projectId, entry.getVisibility(), entry.getName(),
                    entry.getDescription(), entry.getCfg(), entry.getAcceptsRawPayload(), updatedOwnerId, entry.getMeta());

            if (repos != null) {
                repositoryDao.deleteAll(tx, projectId);
                repos.forEach((k, v) -> projectRepositoryManager.insert(tx, orgId, prevEntry.getOrgName(), projectId, prevEntry.getName(), v, false));
            }
        });

        ProjectEntry newEntry = projectDao.get(projectId);

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
        ProjectEntry e = accessManager.assertProjectAccess(projectId, ResourceAccessLevel.OWNER, true);

        projectDao.delete(projectId);

        addAuditLog(
                AuditAction.DELETE,
                e.getOrgId(),
                e.getOrgName(),
                e.getId(),
                e.getName(),
                null);
    }

    public List<ProjectEntry> list(UUID orgId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        if (Roles.isAdmin() || Roles.isGlobalReader() || Roles.isGlobalWriter()) {
            // admins or "global readers" can see any project, so we shouldn't filter projects by user
            userId = null;
        }

        // TODO replace with queueDao#list?
        return projectDao.list(orgId, userId, PROJECTS.PROJECT_NAME, true);
    }

    private UserEntry getOwner(EntityOwner owner, UserEntry defaultOwner) {
        if (owner == null) {
            return defaultOwner;
        }

        if (owner.id() != null) {
            return userManager.get(owner.id())
                    .orElseThrow(() -> new ValidationErrorsException("User not found: " + owner.id()));
        }

        if (owner.username() != null) {
            return userManager.getOrCreate(owner.username(), owner.userDomain(), UserType.LDAP);
        }

        return defaultOwner;
    }

    private void assertSecrets(UUID orgId, Map<String, RepositoryEntry> repos) {
        if (repos == null) {
            return;
        }

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String n = r.getValue().getSecretName();
            if (n == null) {
                continue;
            }
            secretManager.assertAccess(orgId, null, n, ResourceAccessLevel.READER, false);
        }
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, String orgName, UUID projectId, String projectName, Map<String, Object> changes) {
        auditLog.add(AuditObject.PROJECT, auditAction)
                .field("orgId", orgId)
                .field("orgName", orgName)
                .field("projectId", projectId)
                .field("projectName", projectName)
                .field("changes", changes)
                .log();
    }
}
