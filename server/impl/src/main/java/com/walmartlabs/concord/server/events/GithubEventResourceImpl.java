package com.walmartlabs.concord.server.events;

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

import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.repository.CachedRepositoryManager.RepositoryCacheDao;
import static com.walmartlabs.concord.server.repository.RepositoryManager.DEFAULT_BRANCH;

@Named
public class GithubEventResourceImpl extends AbstractEventResource implements GithubEventResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubEventResourceImpl.class);

    private static final String EVENT_SOURCE = "github";

    private static final String ORG_NAME_KEY = "org";
    private static final String REPO_ID_KEY = "repositoryId";
    private static final String REPO_NAME_KEY = "repository";
    private static final String PROJECT_NAME_KEY = "project";
    private static final String REPO_BRANCH_KEY = "branch";
    private static final String COMMIT_ID_KEY = "commitId";
    private static final String PUSHER_KEY = "author";

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final RepositoryCacheDao repositoryCacheDao;
    private final GithubWebhookManager webhookManager;

    @Inject
    public GithubEventResourceImpl(ProjectDao projectDao,
                                   TriggersDao triggersDao,
                                   RepositoryDao repositoryDao,
                                   RepositoryCacheDao repositoryCacheDao,
                                   PayloadManager payloadManager,
                                   ProcessManager processManager,
                                   GithubWebhookManager webhookManager) {

        super(payloadManager, processManager, triggersDao, projectDao);

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.repositoryCacheDao = repositoryCacheDao;
        this.webhookManager = webhookManager;
    }

    @Override
    public String push(Map<String, Object> event) {
        if (event == null) {
            return "ok";
        }

        Map<String, Object> repo = (Map<String, Object>) event.getOrDefault("repository", Collections.emptyMap());
        String repoName = (String)repo.get("full_name");
        if (repoName == null) {
            return "ok";
        }

        List<RepositoryEntry> repos = repositoryDao.find(repoName);
        if (repos.isEmpty()) {
            log.info("push ['{}'] -> repository not found, delete webhook", repoName);
            webhookManager.unregister(repoName);
            return "ok";
        }

        String eventBranch = getBranch(event);

        for (RepositoryEntry r :repos) {
            String rBranch = Optional.ofNullable(r.getBranch()).orElse(DEFAULT_BRANCH);
            if (!rBranch.equals(eventBranch)) {
                continue;
            }

            repositoryCacheDao.updateLastPushDate(r.getId(), new Date());

            ProjectEntry project = projectDao.get(r.getProjectId());

            Map<String, Object> triggerConditions = buildConditions(r, event);
            Map<String, Object> triggerEvent = buildTriggerEvent(event, r, project, triggerConditions);

            String eventId = r.getId().toString();
            int count = process(eventId, EVENT_SOURCE, triggerConditions, triggerEvent);

            log.info("event ['{}', '{}', '{}'] -> {} processes started", eventId, triggerConditions, triggerEvent, count);
        }

        return "ok";
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         RepositoryEntry repo,
                                                         ProjectEntry project,
                                                         Map<String, Object> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put(COMMIT_ID_KEY, event.get("after"));
        result.put(REPO_ID_KEY, repo.getId());
        result.put(PROJECT_NAME_KEY, project.getName());
        result.put(ORG_NAME_KEY, project.getOrgName());
        result.putAll(conditions);
        return result;
    }

    private static String getBranch(Map<String, Object> event) {
        String ref = (String) event.get("ref");
        if (ref == null) {
            return null;
        }

        String[] refPath = ref.split("/");
        return refPath[refPath.length - 1];
    }

    private static Map<String, Object> buildConditions(RepositoryEntry repo, Map<String, Object> event) {
        Map<String, Object> result = new HashMap<>();
        result.put(REPO_NAME_KEY, repo.getName());
        result.put(REPO_BRANCH_KEY, Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH));
        result.put(PUSHER_KEY, getPusher(event));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String getPusher(Map<String, Object> event) {
        Map<String, Object> pusher = (Map<String, Object>) event.get("pusher");
        if (pusher == null) {
            return null;
        }

        return (String) pusher.get("name");
    }

}
