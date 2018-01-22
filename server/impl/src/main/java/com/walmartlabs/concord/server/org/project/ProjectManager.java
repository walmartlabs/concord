package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.events.GithubWebhookService;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

@Named
public class ProjectManager {

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectAccessManager accessManager;
    private final SecretManager secretManager;
    private final GithubWebhookService githubWebhookService;
    private final EventResource eventResource;

    @Inject
    public ProjectManager(ProjectDao projectDao,
                          RepositoryDao repositoryDao,
                          ProjectAccessManager accessManager,
                          SecretManager secretManager,
                          GithubWebhookService githubWebhookService,
                          EventResource eventResource) {

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.accessManager = accessManager;
        this.secretManager = secretManager;
        this.githubWebhookService = githubWebhookService;
        this.eventResource = eventResource;
    }

    public ProjectEntry get(UUID projectId) {
        return accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);
    }

    public UUID insert(UUID orgId, String orgName, ProjectEntry entry) {
        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        UserPrincipal p = UserPrincipal.getCurrent();
        UUID ownerId = p.getId();

        return projectDao.txResult(tx -> {
            boolean acceptsRawPayload = entry.getAcceptsRawPayload() != null ? entry.getAcceptsRawPayload() : false;
            UUID pId = projectDao.insert(tx, orgId, entry.getName(), entry.getDescription(), ownerId, entry.getCfg(),
                    entry.getVisibility(), acceptsRawPayload);

            if (repos != null) {
                Map<String, RepositoryEntry> r = new HashMap<>();

                repos.forEach((repoName, re) -> {
                    boolean success = githubWebhookService.register(pId, repoName, re.getUrl());
                    r.put(repoName, new RepositoryEntry(re.getId(), re.getProjectId(), re.getName(),
                            re.getUrl(), re.getBranch(), re.getCommitId(), re.getPath(), re.getSecret(), success));
                });

                insertRepos(tx, orgId, orgName, pId, entry.getName(), r, false);
            }

            return pId;
        });
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

                Map<String, RepositoryEntry> r = new HashMap<>();

                repos.forEach((repoName, re) -> {
                    boolean success = githubWebhookService.register(projectId, re.getName(), re.getUrl());
                    r.put(re.getName(), new RepositoryEntry(re.getId(), re.getProjectId(), re.getName(),
                            re.getUrl(), re.getBranch(), re.getCommitId(), re.getPath(), re.getSecret(), success));
                });

                insertRepos(tx, orgId, prevEntry.getOrgName(), projectId, entry.getName(), repos, true);
            }
        });
    }

    public void delete(UUID projectId) {
        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, true);

        projectDao.delete(projectId);
    }

    public List<ProjectEntry> list(UUID orgId) {
        UserPrincipal p = UserPrincipal.getCurrent();
        UUID userId = p.getId();
        if (p.isAdmin()) {
            // admins can see any project, so we shouldn't filter projects by user
            userId = null;
        }

        return projectDao.list(orgId, userId, PROJECTS.PROJECT_NAME, true);
    }

    private void assertSecrets(UUID orgId, Map<String, RepositoryEntry> repos) {
        if (repos == null) {
            return;
        }

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String secretName = r.getValue().getSecret();
            if (secretName == null) {
                continue;
            }
            secretManager.assertAccess(orgId, null, secretName, ResourceAccessLevel.READER, false);
        }
    }

    private Map<String, UUID> insertRepos(DSLContext tx, UUID orgId, String orgName,
                                          UUID projectId, String projectName,
                                          Map<String, RepositoryEntry> repos,
                                          boolean update) {

        Map<String, UUID> ids = new HashMap<>();

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String repoName = r.getKey();
            RepositoryEntry req = r.getValue();

            UUID secretId = null;
            if (req.getSecret() != null) {
                SecretEntry e = secretManager.assertAccess(orgId, null, req.getSecret(), ResourceAccessLevel.READER, false);
                secretId = e.getId();
            }

            UUID id = repositoryDao.insert(tx, projectId, repoName, req.getUrl(),
                    trim(req.getBranch()),
                    trim(req.getCommitId()),
                    trim(req.getPath()),
                    secretId,
                    req.isHasWebHook());

            Map<String, Object> ev;
            if (update) {
                ev = Events.Repository.repositoryUpdated(orgName, projectName, repoName);
            } else {
                ev = Events.Repository.repositoryCreated(orgName, projectName, repoName);
            }
            eventResource.event(Events.CONCORD_EVENT, ev);

            ids.put(repoName, id);
        }

        return ids;
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
