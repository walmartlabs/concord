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

import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.events.Events;
import com.walmartlabs.concord.server.events.ExternalEventResource;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.secret.SecretEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import org.jooq.DSLContext;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Named
public class ProjectRepositoryManager {

    private final ProjectAccessManager projectAccessManager;
    private final SecretManager secretManager;
    private final RepositoryManager repositoryManager;
    private final RepositoryDao repositoryDao;
    private final ExternalEventResource externalEventResource;
    private final AuditLog auditLog;
    private final ProjectLoader projectLoader;
    private final ImportsNormalizerFactory importsNormalizerFactory;
    private final PolicyManager policyManager;

    @Inject
    public ProjectRepositoryManager(ProjectAccessManager projectAccessManager,
                                    SecretManager secretManager,
                                    RepositoryManager repositoryManager,
                                    RepositoryDao repositoryDao,
                                    ExternalEventResource externalEventResource,
                                    AuditLog auditLog,
                                    ProjectLoader projectLoader,
                                    ImportsNormalizerFactory importsNormalizerFactory,
                                    PolicyManager policyManager) {

        this.projectAccessManager = projectAccessManager;
        this.secretManager = secretManager;
        this.repositoryManager = repositoryManager;
        this.repositoryDao = repositoryDao;
        this.externalEventResource = externalEventResource;
        this.auditLog = auditLog;
        this.projectLoader = projectLoader;
        this.importsNormalizerFactory = importsNormalizerFactory;
        this.policyManager = policyManager;
    }

    public RepositoryEntry get(UUID projectId, String repositoryName) {
        return repositoryDao.get(projectId, repositoryName);
    }

    public List<RepositoryEntry> list(UUID projectId) {
        return repositoryDao.list(projectId);
    }

    public List<RepositoryEntry> list(UUID orgId, String projectName, int offset, int limit, String filter) {
        ProjectEntry project = projectAccessManager.assertAccess(orgId, null, projectName, ResourceAccessLevel.READER, false);
        return repositoryDao.list(project.getId(), Tables.REPOSITORIES.REPO_NAME, true, offset, limit, filter);
    }

    public void createOrUpdate(UUID projectId, RepositoryEntry entry) {
        repositoryDao.tx(tx -> createOrUpdate(tx, projectId, entry));
    }

    public void createOrUpdate(DSLContext tx, UUID projectId, RepositoryEntry entry) {
        ProjectEntry project = projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, true);

        UUID repoId = entry.getId();
        if (repoId == null) {
            repoId = repositoryDao.getId(projectId, entry.getName());
        }

        if (repoId == null) {
            insert(tx, project.getOrgId(), project.getOrgName(), projectId, project.getName(), entry, true);
        } else {
            update(tx, project.getOrgId(), project.getOrgName(), projectId, project.getName(), repoId, entry);
        }
    }

    public void delete(UUID projectId, String repoName) {
        ProjectEntry projEntry = projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, false);

        RepositoryEntry r = repositoryDao.get(projectId, repoName);
        if (r == null) {
            throw new ValidationErrorsException("Repository not found: " + repoName);
        }

        repositoryDao.delete(r.getId());
        addAuditLog(
                projEntry.getOrgId(),
                projEntry.getOrgName(),
                projEntry.getId(),
                projEntry.getName(),
                r,
                null);
    }

    public void insert(DSLContext tx, UUID orgId, String orgName, UUID projectId, String projectName, RepositoryEntry entry, boolean doAuditLog) {
        RepositoryUtils.assertRepository(entry);

        SecretEntry secret = assertSecret(orgId, entry);

        policyManager.checkEntity(orgId, projectId, EntityType.REPOSITORY, EntityAction.CREATE, null,
                PolicyUtils.repositoryToMap(orgId, orgName, projectId, projectName, entry, secret));

        UUID repoId = repositoryDao.insert(tx, projectId,
                entry.getName(), entry.getUrl(),
                trim(entry.getBranch()),
                trim(entry.getCommitId()),
                trim(entry.getPath()),
                secret == null ? null : secret.getId(),
                entry.isDisabled(),
                entry.getMeta(),
                entry.isTriggersDisabled());

        Map<String, Object> ev = Events.Repository.repositoryCreated(projectId, repoId, entry.getName(), !entry.isDisabled());
        externalEventResource.event(Events.CONCORD_EVENT, ev);

        if (doAuditLog) {
            RepositoryEntry newEntry = repositoryDao.get(tx, projectId, repoId);
            addAuditLog(
                    orgId,
                    orgName,
                    projectId,
                    projectName,
                    null,
                    newEntry
            );
        }
    }

    private void update(DSLContext tx, UUID orgId, String orgName, UUID projectId, String projectName, UUID repoId, RepositoryEntry entry) {
        RepositoryUtils.assertRepository(entry);

        RepositoryEntry prevEntry = repositoryDao.get(tx, projectId, repoId);

        SecretEntry secret = assertSecret(orgId, entry);
        policyManager.checkEntity(orgId, projectId, EntityType.REPOSITORY, EntityAction.UPDATE, null,
                PolicyUtils.repositoryToMap(orgId, orgName, projectId, projectName, entry, secret));

        repositoryDao.update(tx, repoId,
                entry.getName(),
                entry.getUrl(),
                trim(entry.getBranch()),
                trim(entry.getCommitId()),
                trim(entry.getPath()),
                secret == null ? null : secret.getId(),
                entry.isDisabled(),
                entry.isTriggersDisabled());

        Map<String, Object> ev = Events.Repository.repositoryUpdated(projectId,
                repoId,
                entry.getName(),
                !entry.isDisabled());
        externalEventResource.event(Events.CONCORD_EVENT, ev);

        RepositoryEntry newEntry = repositoryDao.get(tx, projectId, repoId);
        addAuditLog(
                orgId,
                orgName,
                projectId,
                projectName,
                prevEntry,
                newEntry
        );
    }

    public ProjectValidator.Result validateRepository(UUID projectId, RepositoryEntry repo) {
        try {
            ProcessDefinition pd = repositoryManager.withLock(repo.getUrl(), () -> {
                Repository repository = repositoryManager.fetch(projectId, repo);
                ProjectLoader.Result result = projectLoader.loadProject(repository.path(), importsNormalizerFactory.forProject(repo.getProjectId()), ImportsListener.NOP_LISTENER);
                return result.projectDefinition();
            });

            return ProjectValidator.validate(pd);
        } catch (Exception e) {
            throw new RepositoryValidationException("Validation failed: " + repo.getName(), e);
        }
    }

    private SecretEntry assertSecret(UUID orgId, RepositoryEntry entry) {
        if (entry.getSecretId() == null && entry.getSecretName() == null) {
            return null;
        }
        return secretManager.assertAccess(orgId, entry.getSecretId(), entry.getSecretName(), ResourceAccessLevel.READER, false);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.isEmpty()) {
            return null;
        }

        return s;
    }

    private void addAuditLog(UUID orgId, String orgName, UUID projectId, String projectName, RepositoryEntry prevRepoEntry, RepositoryEntry newRepoEntry) {
        ProjectEntry prevEntry = new ProjectEntry(null, new HashMap<>());
        if (prevRepoEntry != null) {
            prevEntry.getRepositories().put(prevRepoEntry.getName(), prevRepoEntry);
        }

        ProjectEntry newEntry = new ProjectEntry(null, new HashMap<>());
        if (newRepoEntry != null) {
            newEntry.getRepositories().put(newRepoEntry.getName(), newRepoEntry);
        }

        Map<String, Object> changes = DiffUtils.compare(prevEntry, newEntry);

        auditLog.add(AuditObject.PROJECT, AuditAction.UPDATE)
                .field("orgId", orgId)
                .field("orgName", orgName)
                .field("projectId", projectId)
                .field("name", projectName)
                .field("changes", changes)
                .log();
    }
}
