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

import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.events.github.GithubRepoInfo;
import com.walmartlabs.concord.server.events.github.GithubUtils;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.events.github.Constants.*;

/**
 * Enrich "github" trigger definitions. The main goal is to save trigger
 * definitions in the DB so they can be used later to match with external events
 * (GitHub push notifications, for example).
 */
public class GithubTriggerEnricher {

    private final RepositoryDao repositoryDao;

    @Inject
    public GithubTriggerEnricher(RepositoryDao repositoryDao) {
        this.repositoryDao = repositoryDao;
    }

    public Map<String, Object> enrich(DSLContext tx, UUID repoId, Map<String, Object> conditions) {
        if (conditions == null) {
            return conditions;
        }

        // determine the trigger definition's version
        // version 1 is the original implementation
        // version 2 is a streamlined implementation that doesn't support some of the corner cases of v1
        int triggerVersion = MapUtils.getInt(conditions, VERSION_KEY, 1);
        if (triggerVersion == 2) {
            conditions = enrichTriggerConditions(tx, repoId, conditions);
        }
        return conditions;
    }

    private Map<String, Object> enrichTriggerConditions(DSLContext tx, UUID repoId, Map<String, Object> conditions) {
        if (conditions.containsKey(GITHUB_ORG_KEY) && conditions.containsKey(GITHUB_REPO_KEY) && conditions.containsKey(REPO_BRANCH_KEY)) {
            return conditions;
        }

        RepositoryEntry repo = repositoryDao.get(tx, repoId);
        GithubRepoInfo githubRepoInfo = GithubUtils.getRepositoryInfo(repo.getUrl());
        if (githubRepoInfo == null) {
            return conditions;
        }

        Map<String, Object> newParams = new HashMap<>(conditions);
        newParams.putIfAbsent(GITHUB_ORG_KEY, githubRepoInfo.owner());
        newParams.putIfAbsent(GITHUB_REPO_KEY, githubRepoInfo.name());

        Object eventType = conditions.get(TYPE_KEY);
        if ((PULL_REQUEST_EVENT.equals(eventType) || PUSH_EVENT.equals(eventType)) && (repo.getBranch() != null)) {
            newParams.putIfAbsent(REPO_BRANCH_KEY, repo.getBranch());
        }
        return newParams;
    }
}
