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

import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.secret.SecretEntryV2;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.triggers.TriggerManager;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.repository.RepositoryRefresher;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.immutables.value.Value;
import org.jooq.DSLContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.*;

public class ProjectRepositoryManager {

    private final ProjectAccessManager projectAccessManager;
    private final SecretManager secretManager;
    private final RepositoryDao repositoryDao;
    private final AuditLog auditLog;
    private final PolicyManager policyManager;
    private final RepositoryRefresher repositoryRefresher;
    private final TriggerManager triggerManager;

    @Inject
    public ProjectRepositoryManager(ProjectAccessManager projectAccessManager,
                                    SecretManager secretManager,
                                    RepositoryDao repositoryDao,
                                    AuditLog auditLog,
                                    PolicyManager policyManager,
                                    RepositoryRefresher repositoryRefresher,
                                    TriggerManager triggerManager) {

        this.projectAccessManager = projectAccessManager;
        this.secretManager = secretManager;
        this.repositoryDao = repositoryDao;
        this.auditLog = auditLog;
        this.policyManager = policyManager;
        this.repositoryRefresher = repositoryRefresher;
        this.triggerManager = triggerManager;
    }

    public RepositoryEntry get(UUID projectId, String repositoryName) {
        RepositoryEntry r = repositoryDao.get(projectId, repositoryName);

        if (r == null) {
            throw new ConcordApplicationException("Repository not found: " + repositoryName, Response.Status.NOT_FOUND);
        }

        return r;
    }

    public RepositoryEntry get(UUID orgId, String projectName, String repositoryName) {
        ProjectEntry project = projectAccessManager.assertAccess(orgId, null, projectName, ResourceAccessLevel.READER, false);
        return get(project.getId(), repositoryName);
    }

    public List<RepositoryEntry> list(UUID projectId) {
        return repositoryDao.list(projectId);
    }

    public List<RepositoryEntry> list(UUID orgId, String projectName, int offset, int limit, String filter) {
        ProjectEntry project = projectAccessManager.assertAccess(orgId, null, projectName, ResourceAccessLevel.READER, false);
        return repositoryDao.list(project.getId(), Tables.REPOSITORIES.REPO_NAME, true, offset, limit, filter);
    }

    public void createOrUpdate(UUID projectId, RepositoryEntry entry) {
        ProjectEntry project = projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, true);

        UUID repoId = entry.getId() != null ? entry.getId() : repositoryDao.getId(projectId, entry.getName());

        SecretEntryV2 secret = assertSecret(project.getOrgId(), entry);

        policyManager.checkEntity(project.getOrgId(), project.getId(), EntityType.REPOSITORY, repoId == null ? EntityAction.CREATE : EntityAction.UPDATE,
                null, PolicyUtils.repositoryToMap(project, entry, secret));

        ProcessDefinition processDefinition = entry.isDisabled()
                ? null
                : processDefinition(project.getOrgId(), projectId, entry);

        InsertUpdateResult result = repositoryDao.txResult(tx -> insertOrUpdate(tx, projectId, repoId, entry, secret, processDefinition));
        addAuditLog(project, result.prevEntry(), result.newEntry());
    }

    public void replace(DSLContext tx, UUID orgId, UUID projectId, Collection<RepositoryEntry> repos, Map<String, ProcessDefinition> processDefinitions) {
        repositoryDao.deleteAll(tx, projectId);

        for (RepositoryEntry re : repos) {
            SecretEntryV2 secret = assertSecret(orgId, re);
            insertOrUpdate(tx, projectId, null, re, secret, processDefinitions.get(re.getName()));
        }
    }

    public void delete(UUID projectId, String repoName) {
        ProjectEntry projEntry = projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, false);

        RepositoryEntry r = repositoryDao.get(projectId, repoName);
        if (r == null) {
            throw new ValidationErrorsException("Repository not found: " + repoName);
        }

        repositoryDao.delete(r.getId());
        addAuditLog(projEntry, r, null);
    }

    public ProjectValidator.Result validateRepository(UUID orgId, RepositoryEntry repo) {
        try {
            ProcessDefinition pd = processDefinition(orgId, repo.getProjectId(), repo);
            return ProjectValidator.validate(pd);
        } catch (Exception e) {
            throw new RepositoryValidationException("Validation failed: " + repo.getName(), e);
        }
    }

    public ProcessDefinition processDefinition(UUID orgId, UUID projectId, RepositoryEntry repositoryEntry) {
        return repositoryRefresher.processDefinition(orgId, projectId, repositoryEntry);
    }

    @Value.Immutable
    interface InsertUpdateResult {

        @Nullable
        RepositoryEntry prevEntry();

        RepositoryEntry newEntry();
    }

    private InsertUpdateResult insertOrUpdate(DSLContext tx, UUID projectId, UUID repoId, RepositoryEntry entry, SecretEntryV2 secret, ProcessDefinition processDefinition) {
        if (!entry.isDisabled() && processDefinition == null) {
            // should have already thrown and exception by this point, but just in case
            // something went wrong cloning/loading process definition
            throw new ConcordApplicationException("Error while loading process definition", Response.Status.INTERNAL_SERVER_ERROR);
        }

        RepositoryEntry prevEntry = null;
        if (repoId == null) {
            repoId = repositoryDao.insert(tx, projectId,
                    entry.getName(), entry.getUrl(),
                    trim(entry.getBranch()),
                    trim(entry.getCommitId()),
                    trim(entry.getPath()),
                    secret == null ? null : secret.getId(),
                    entry.isDisabled(),
                    entry.getMeta(),
                    entry.isTriggersDisabled());
        } else {
            prevEntry = repositoryDao.get(tx, projectId, repoId);

            repositoryDao.update(tx, repoId,
                    entry.getName(),
                    entry.getUrl(),
                    trim(entry.getBranch()),
                    trim(entry.getCommitId()),
                    trim(entry.getPath()),
                    secret == null ? null : secret.getId(),
                    entry.isDisabled(),
                    entry.isTriggersDisabled());
        }

        if (entry.isDisabled()) {
            triggerManager.clearTriggers(tx, projectId, repoId);
        } else {
            repositoryRefresher.refresh(tx, projectId, entry.getName(), processDefinition);
        }

        return ImmutableInsertUpdateResult.builder()
                .prevEntry(prevEntry)
                .newEntry(repositoryDao.get(tx, projectId, repoId))
                .build();
    }

    private SecretEntryV2 assertSecret(UUID orgId, RepositoryEntry entry) {
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

    private void addAuditLog(ProjectEntry project, RepositoryEntry prevRepoEntry, RepositoryEntry newRepoEntry) {
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
                .field("orgId", project.getOrgId())
                .field("orgName", project.getOrgName())
                .field("projectId", project.getId())
                .field("name", project.getName())
                .field("changes", changes)
                .log();
    }
}
