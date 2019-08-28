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

import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.security.github.GithubKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.events.github.Constants.*;

@Named
@Singleton
public class GithubTriggerV2Processor implements GithubTriggerProcessor {

    private final TriggersDao dao;

    @Inject
    public GithubTriggerV2Processor(TriggersDao dao) {
        this.dao = dao;
    }

    @Override
    public void process(String eventName, Payload payload, UriInfo uriInfo, List<Result> result) {
        Map<String, Object> triggerConditions = buildConditions(eventName, payload);
        Map<String, Object> triggerEvent = buildTriggerEvent(payload, triggerConditions);

        GithubKey githubKey = GithubKey.getCurrent();
        UUID projectId = githubKey.getProjectId();

        List<TriggerEntry> triggers = listTriggers(projectId, payload.getOrg(), payload.getRepo()).stream()
                .filter(t -> DefaultEventFilter.filter(triggerConditions, t))
                .collect(Collectors.toList());

        if (!triggers.isEmpty()) {
            result.add(Result.from(triggerEvent, triggers));
        }
    }

    private List<TriggerEntry> listTriggers(UUID projectId, String org, String repo) {
        Map<String, String> conditions = new HashMap<>();
        conditions.put(GITHUB_ORG_KEY, org);
        conditions.put(GITHUB_REPO_KEY, repo);
        return dao.list(projectId, EVENT_SOURCE, 2, conditions);
    }

    private Map<String, Object> buildConditions(String eventName, Payload payload) {
        Map<String, Object> result = new HashMap<>();

        result.put(GITHUB_ORG_KEY, payload.getOrg());
        result.put(GITHUB_REPO_KEY, payload.getRepo());
        result.put(GITHUB_HOST_KEY, payload.getHost());
        String branch = payload.getBranch();
        if (branch != null) {
            result.put(REPO_BRANCH_KEY, payload.getBranch());
        }
        result.put(SENDER_KEY, payload.getSender());
        result.put(TYPE_KEY, eventName);
        result.put(STATUS_KEY, payload.getAction());
        result.put(PAYLOAD_KEY, payload.raw());
        result.put(VERSION, 2);
        return result;
    }

    private static Map<String, Object> buildTriggerEvent(Payload payload, Map<String, Object> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put(COMMIT_ID_KEY, payload.getString("after"));
        result.putAll(conditions);
        result.put(PAYLOAD_KEY, payload.raw());
        return result;
    }
}
