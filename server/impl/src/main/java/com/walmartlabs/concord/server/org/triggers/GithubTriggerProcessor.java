package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.repository.GitCliRepositoryProvider;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.events.github.GithubRepoInfo;
import com.walmartlabs.concord.server.events.github.GithubUtils;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.events.github.Constants.*;

/**
 * Processes "github" trigger definitions. The main goal is to save trigger
 * definitions in the DB so they can be used later to match with external events
 * (GitHub push notifications, for example).
 */
@Named("github")
public class GithubTriggerProcessor implements TriggerProcessor {

    private final TriggersDao triggersDao;
    private final RepositoryDao repositoryDao;

    @Inject
    public GithubTriggerProcessor(TriggersDao triggersDao, RepositoryDao repositoryDao) {
        this.triggersDao = triggersDao;
        this.repositoryDao = repositoryDao;
    }

    @Override
    public void process(DSLContext tx, UUID repoId, UUID triggerId, Trigger t) {
        Map<String, Object> params = t.getParams();
        if (params == null) {
            return;
        }

        // determine the trigger definition's version
        // version 1 is the original implementation
        // version 2 is a streamlined implementation that doesn't support some of the corner cases of v1
        int triggerVersion = MapUtils.getInt(params, VERSION, 1);
        if (triggerVersion == 2) {
            params = enrichTriggerConditions(tx, repoId, t);
            triggersDao.update(tx, triggerId, params, 2);
        }
    }

    private Map<String, Object> enrichTriggerConditions(DSLContext tx, UUID repoId, Trigger t) {
        Map<String, Object> params = t.getParams();
        if (params.containsKey(GITHUB_ORG_KEY) && params.containsKey(GITHUB_REPO_KEY) && params.containsKey(REPO_BRANCH_KEY)) {
            return params;
        }

        RepositoryEntry repo = repositoryDao.get(tx, repoId);
        GithubRepoInfo githubRepoInfo = GithubUtils.getRepositoryInfo(repo.getUrl());
        if (githubRepoInfo == null) {
            return params;
        }

        Map<String, Object> newParams = new HashMap<>(t.getParams());
        newParams.putIfAbsent(GITHUB_ORG_KEY, githubRepoInfo.owner());
        newParams.putIfAbsent(GITHUB_REPO_KEY, githubRepoInfo.name());

        Object eventType = params.get(TYPE_KEY);
        if (PULL_REQUEST_EVENT.equals(eventType) || PUSH_EVENT.equals(eventType)) {
            String defaultBranch = repo.getBranch() != null ? repo.getBranch() : GitCliRepositoryProvider.DEFAULT_BRANCH;
            newParams.putIfAbsent(REPO_BRANCH_KEY, defaultBranch);
        }
        return newParams;
    }
}
