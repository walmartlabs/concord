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

import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.events.GithubWebhookService;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

@Named
public class ProjectManager {

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final ProjectAccessManager accessManager;
    private final SecretManager secretManager;
    private final AuditLog auditLog;

    @Inject
    public ProjectManager(ProjectDao projectDao,
                          RepositoryDao repositoryDao,
                          ProjectRepositoryManager projectRepositoryManager,
                          ProjectAccessManager accessManager,
                          SecretManager secretManager,
                          AuditLog auditLog) {

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.projectRepositoryManager = projectRepositoryManager;
        this.accessManager = accessManager;
        this.secretManager = secretManager;
        this.auditLog = auditLog;
    }

    public ProjectEntry get(UUID projectId) {
        return accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);
    }

    public UUID insert(UUID orgId, String orgName, ProjectEntry entry) {
        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID ownerId = p.getId();

        UUID id = projectDao.txResult(tx -> {
            boolean acceptsRawPayload = entry.getAcceptsRawPayload() != null ? entry.getAcceptsRawPayload() : false;
            UUID pId = projectDao.insert(tx, orgId, entry.getName(), entry.getDescription(), ownerId, entry.getCfg(),
                    entry.getVisibility(), acceptsRawPayload);

            if (repos != null) {
                repos.forEach((k, v) -> projectRepositoryManager.insert(tx, orgId, orgName, pId, entry.getName(), v));
            }

            return pId;
        });

        // TODO more details?
        auditLog.add(AuditObject.PROJECT, AuditAction.CREATE)
                .field("id", id)
                .field("orgId", orgId)
                .field("name", entry.getName())
                .log();

        return id;
    }

    public void update(UUID projectId, ProjectEntry entry) {
        ProjectEntry prevEntry = accessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, true);
        UUID orgId = prevEntry.getOrgId();

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        projectDao.tx(tx -> {
            projectDao.update(tx, orgId, projectId, entry.getVisibility(), entry.getName(),
                    entry.getDescription(), entry.getCfg(), entry.getAcceptsRawPayload());

            if (repos != null) {
                repositoryDao.deleteAll(tx, projectId);
                repos.forEach((k, v) -> projectRepositoryManager.insert(tx, orgId, prevEntry.getOrgName(), projectId, prevEntry.getName(), v));
            }
        });

        // TODO more details, delta?
        auditLog.add(AuditObject.PROJECT, AuditAction.UPDATE)
                .field("id", projectId)
                .log();
    }

    public void delete(UUID projectId) {
        ProjectEntry e = accessManager.assertProjectAccess(projectId, ResourceAccessLevel.OWNER, true);

        projectDao.delete(projectId);

        auditLog.add(AuditObject.PROJECT, AuditAction.DELETE)
                .field("id", projectId)
                .field("orgId", e.getOrgId())
                .field("name", e.getName())
                .log();
    }

    public List<ProjectEntry> list(UUID orgId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        if (p.isAdmin() || p.isGlobalReader() || p.isGlobalWriter()) {
            // admins or "global readers" can see any project, so we shouldn't filter projects by user
            userId = null;
        }

        return projectDao.list(orgId, userId, PROJECTS.PROJECT_NAME, true);
    }

    private void assertSecrets(UUID orgId, Map<String, RepositoryEntry> repos) {
        if (repos == null) {
            return;
        }

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String secretName = r.getValue().getSecretName();
            if (secretName == null) {
                continue;
            }
            secretManager.assertAccess(orgId, null, secretName, ResourceAccessLevel.READER, false);
        }
    }
}
