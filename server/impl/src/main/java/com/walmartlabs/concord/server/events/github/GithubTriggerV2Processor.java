package com.walmartlabs.concord.server.events.github;

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

import com.walmartlabs.concord.repository.GitCliRepositoryProvider;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.github.GithubKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static com.walmartlabs.concord.server.events.github.Constants.*;

@Named
@Singleton
public class GithubTriggerV2Processor implements GithubTriggerProcessor {

    private static final int VERSION_ID = 2;

    private final TriggersDao dao;
    private final List<EventEnricher> eventEnrichers;

    @Inject
    public GithubTriggerV2Processor(TriggersDao dao, List<EventEnricher> eventEnrichers) {
        this.dao = dao;
        this.eventEnrichers = eventEnrichers;
    }

    @Override
    @WithTimer
    public void process(String eventName, Payload payload, UriInfo uriInfo, List<Result> result) {
        GithubKey githubKey = GithubKey.getCurrent();
        UUID projectId = githubKey.getProjectId();

        List<TriggerEntry> triggers = listTriggers(projectId, payload.getOrg(), payload.getRepo());
        for (TriggerEntry t : triggers) {
            // skip empty push events if the trigger's configuration says so
            if (GithubUtils.ignoreEmptyPush(t) && GithubUtils.isEmptyPush(eventName, payload)) {
                continue;
            }

            Map<String, Object> event = buildEvent(eventName, payload);
            enrichEventConditions(payload, t, event);

            if (DefaultEventFilter.filter(event, t)) {
                result.add(Result.from(event, t));
            }
        }
    }

    private void enrichEventConditions(Payload payload, TriggerEntry trigger, Map<String, Object> result) {
        for (EventEnricher e : eventEnrichers) {
            e.enrich(payload, trigger, result);
        }
    }

    @WithTimer
    List<TriggerEntry> listTriggers(UUID projectId, String org, String repo) {
        Map<String, String> conditions = new HashMap<>();

        if (org != null) {
            conditions.put(GITHUB_ORG_KEY, org);
        }

        if (repo != null) {
            conditions.put(GITHUB_REPO_KEY, repo);
        }

        return dao.list(projectId, EVENT_SOURCE, VERSION_ID, conditions);
    }

    private Map<String, Object> buildEvent(String eventName, Payload payload) {
        Map<String, Object> result = new HashMap<>();

        result.put(GITHUB_ORG_KEY, payload.getOrg());
        result.put(GITHUB_REPO_KEY, payload.getRepo());
        result.put(GITHUB_HOST_KEY, payload.getHost());
        String branch = payload.getBranch();
        if (branch != null) {
            result.put(REPO_BRANCH_KEY, payload.getBranch());
        }

        if (PULL_REQUEST_EVENT.equals(eventName)) {
            Map<String, Object> pullRequest = MapUtils.getMap(payload.raw(), PULL_REQUEST_EVENT, Collections.emptyMap());
            Map<String, Object> head = MapUtils.getMap(pullRequest, "head", Collections.emptyMap());
            String sha = MapUtils.getString(head, "sha");
            if (sha != null) {
                result.put(COMMIT_ID_KEY, sha);
            }
        } else if (PUSH_EVENT.equals(eventName)) {
            String after = payload.getString("after");
            if (after != null) {
                result.put(COMMIT_ID_KEY, after);
            }
        }

        result.put(SENDER_KEY, payload.getSender());
        result.put(TYPE_KEY, eventName);
        result.put(STATUS_KEY, payload.getAction());
        result.put(PAYLOAD_KEY, payload.raw());

        // match only with v2 triggers
        result.put(VERSION_KEY, VERSION_ID);

        return result;
    }

    interface EventEnricher {

        void enrich(Payload payload, TriggerEntry trigger, Map<String, Object> result);
    }

    /**
     * Adds {@link com.walmartlabs.concord.sdk.Constants.Trigger#REPOSITORY_INFO} property to
     * the event, but only if the trigger's conditions contained the clause with the same key.
     */
    @Named
    private static class RepositoryInfoEnricher implements EventEnricher {

        private final RepositoryDao repositoryDao;

        @Inject
        public RepositoryInfoEnricher(RepositoryDao repositoryDao) {
            this.repositoryDao = repositoryDao;
        }

        @Override
        @WithTimer
        public void enrich(Payload payload, TriggerEntry trigger, Map<String, Object> result) {
            Object projectInfoConditions = trigger.getConditions().get(com.walmartlabs.concord.sdk.Constants.Trigger.REPOSITORY_INFO);
            if (projectInfoConditions == null || payload.getFullRepoName() == null) {
                return;
            }

            List<Map<String, Object>> repositoryInfos = new ArrayList<>();
            List<RepositoryEntry> repositories = repositoryDao.find(payload.getFullRepoName());
            for (RepositoryEntry r : repositories) {
                Map<String, Object> repositoryInfo = new HashMap<>();
                repositoryInfo.put(REPO_ID_KEY, r.getId());
                repositoryInfo.put(REPO_NAME_KEY, r.getName());
                repositoryInfo.put(PROJECT_ID_KEY, r.getProjectId());
                repositoryInfo.put(REPO_BRANCH_KEY, r.getBranch() != null ? r.getBranch() : GitCliRepositoryProvider.DEFAULT_BRANCH);

                repositoryInfos.add(repositoryInfo);
            }

            if (!repositoryInfos.isEmpty()) {
                result.put(com.walmartlabs.concord.sdk.Constants.Trigger.REPOSITORY_INFO, repositoryInfos);
            }
        }
    }
}
