package com.walmartlabs.concord.server.events.github;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.sdk.Constants.Trigger.REPOSITORY_INFO;
import static com.walmartlabs.concord.server.events.github.Constants.*;

@Named
public class RepositoryInfoFilter implements ConditionsFilter {

    private final RepositoryDao repositoryDao;

    public static final int PRIORITY = BasicConditionsFilter.PRIORITY + 1;

    @Inject
    public RepositoryInfoFilter(RepositoryDao repositoryDao) {
        this.repositoryDao = repositoryDao;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Map<String, Object> preprocess(Map<String, Object> conditions) {
        if (!conditions.containsKey(REPOSITORY_INFO)) {
            return conditions;
        }

        Map<String, Object> result = new HashMap<>(conditions);
        result.remove(REPOSITORY_INFO);
        return result;
    }

    @Override
    public boolean filter(Payload payload, TriggerEntry trigger, Map<String, Object> triggerConditions, Map<String, Object> event) {
        Object projectInfoConditions =
                trigger.getConditions().get(REPOSITORY_INFO);
        if (projectInfoConditions == null || payload.getFullRepoName() == null) {
            return true;
        }

        List<Map<String, Object>> repositoryInfos = new ArrayList<>();
        List<RepositoryEntry> repositories =
                repositoryDao.findSimilar("%[/:]" + payload.getFullRepoName() + "(.git)?/?");

        for (RepositoryEntry r : repositories) {
            Map<String, Object> repositoryInfo = new HashMap<>();
            repositoryInfo.put(REPO_ID_KEY, r.getId());
            repositoryInfo.put(REPO_NAME_KEY, r.getName());
            repositoryInfo.put(PROJECT_ID_KEY, r.getProjectId());
            if(r.getBranch() != null)
                repositoryInfo.put(REPO_BRANCH_KEY, r.getBranch());
            repositoryInfo.put(REPO_ENABLED_KEY, !r.isDisabled());

            repositoryInfos.add(repositoryInfo);
        }

        if (repositoryInfos.isEmpty()) {
            return false;
        }

        event.put(REPOSITORY_INFO, repositoryInfos);

        Map<String, Object> conditions = Collections.singletonMap(REPOSITORY_INFO, projectInfoConditions);
        return DefaultEventFilter.filter(event, conditions);
    }
}
