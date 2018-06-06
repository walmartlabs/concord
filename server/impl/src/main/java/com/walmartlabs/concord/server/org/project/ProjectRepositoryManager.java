package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.events.EventResource;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretEntry;
import com.walmartlabs.concord.server.events.Events;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import org.jooq.DSLContext;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;
import java.util.UUID;

@Named
public class ProjectRepositoryManager {

    private final ProjectAccessManager projectAccessManager;
    private final SecretManager secretManager;
    private final SecretDao secretDao;
    private final RepositoryDao repositoryDao;
    private final EventResource eventResource;

    @Inject
    public ProjectRepositoryManager(ProjectAccessManager projectAccessManager,
                                    SecretManager secretManager,
                                    SecretDao secretDao,
                                    RepositoryDao repositoryDao,
                                    EventResource eventResource) {

        this.projectAccessManager = projectAccessManager;
        this.secretManager = secretManager;
        this.secretDao = secretDao;
        this.repositoryDao = repositoryDao;
        this.eventResource = eventResource;
    }

    public void createOrUpdate(UUID projectId, RepositoryEntry entry) {
        repositoryDao.tx(tx -> createOrUpdate(tx, projectId, entry));
    }

    public void createOrUpdate(DSLContext tx, UUID projectId, RepositoryEntry entry) {
        ProjectEntry project = projectAccessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, false);

        UUID secretId = entry.getSecretId();
        if (secretId == null && entry.getSecretName() != null) {
            SecretEntry e = secretManager.assertAccess(project.getOrgId(), null, entry.getSecretName(), ResourceAccessLevel.READER, false);
            secretId = e.getId();
        }

        UUID repoId = entry.getId();
        if (repoId == null) {
            repoId = repositoryDao.getId(projectId, entry.getName());
        }

        Map<String, Object> ev;

        if (repoId == null) {
            repositoryDao.insert(tx, projectId,
                    entry.getName(), entry.getUrl(),
                    trim(entry.getBranch()), trim(entry.getCommitId()),
                    trim(entry.getPath()), secretId, false);

            ev = Events.Repository.repositoryUpdated(project.getOrgName(), project.getName(), entry.getName());
        } else {
            repositoryDao.update(tx, repoId,
                    entry.getName(), entry.getUrl(),
                    trim(entry.getBranch()), trim(entry.getCommitId()),
                    trim(entry.getPath()), secretId);

            ev = Events.Repository.repositoryUpdated(project.getOrgName(), project.getName(), entry.getName());
        }

        eventResource.event(Events.CONCORD_EVENT, ev);

        // TODO audit log
    }

    public void delete(UUID projectId, String repoName) {
        projectAccessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, false);

        UUID repoId = repositoryDao.getId(projectId, repoName);
        if (repoId == null) {
            throw new ValidationErrorsException("Repository not found: " + repoName);
        }

        repositoryDao.delete(repoId);

        // TODO audit log
    }

    public void insert(DSLContext tx, UUID orgId, String orgName, UUID projectId, String projectName, RepositoryEntry entry) {
        UUID secretId = entry.getSecretId();
        if (secretId == null && entry.getSecretName() != null) {
            secretId = secretDao.getId(orgId, entry.getSecretName());
        }

        repositoryDao.insert(tx, projectId,
                entry.getName(), entry.getUrl(),
                trim(entry.getBranch()), trim(entry.getCommitId()),
                trim(entry.getPath()), secretId, false);

        Map<String, Object> ev = Events.Repository.repositoryUpdated(orgName, projectName, entry.getName());
        eventResource.event(Events.CONCORD_EVENT, ev);
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
}
