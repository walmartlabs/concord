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

import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.github.GithubKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.UriInfo;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.events.github.Constants.*;

@Named
@Singleton
public class GithubTriggerV2Processor implements GithubTriggerProcessor {

    private static final int VERSION_ID = 2;

    private final TriggersDao dao;

    private final List<ConditionsFilter> filters;

    @Inject
    public GithubTriggerV2Processor(TriggersDao dao, List<ConditionsFilter> filters) {
        this.dao = dao;
        this.filters = filters.stream()
                .sorted(Comparator.comparingInt(ConditionsFilter::priority))
                .collect(Collectors.toList());
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
            Map<String, Object> triggerConditions = t.getConditions() == null ? Collections.emptyMap() : t.getConditions();
            for (ConditionsFilter f : filters) {
                triggerConditions = f.preprocess(triggerConditions);
            }

            boolean match = true;
            for (ConditionsFilter f : filters) {
                if (!f.filter(payload, t, triggerConditions, event)) {
                    match = false;
                    break;
                }
            }

            if (match) {
                result.add(Result.from(event, t));
            }
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

        // files
        Map<String, Set<String>> files = new HashMap<>(payload.getFiles());
        // alias for all files (changed/modified/deleted)
        files.put("any", files.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet()));
        result.put(FILES_KEY, files);

        // match only with v2 triggers
        result.put(VERSION_KEY, VERSION_ID);

        return result;
    }
}
