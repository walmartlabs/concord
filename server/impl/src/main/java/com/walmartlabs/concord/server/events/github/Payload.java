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

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static com.walmartlabs.concord.server.events.github.Constants.*;

public class Payload {

    public static Payload from(String eventName, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Map<String, Object> repo = MapUtils.getMap(data, REPO_NAME_KEY, Collections.emptyMap());
        String fullRepoName = MapUtils.getString(repo, "full_name");
        if (fullRepoName == null) {
            return null;
        }
        String[] orgRepo = fullRepoName.split("/");
        if (orgRepo.length < 2) {
            return null;
        }

        return new Payload(eventName, fullRepoName, orgRepo[0], orgRepo[1], data);
    }

    private final String eventName;
    private final Map<String, Object> data;
    private final String fullRepoName;
    private final String org;
    private final String repo;

    protected Payload(String eventName, String fullRepoName, String org, String repo, Map<String, Object> data) {
        this.eventName = eventName;
        this.data = data;
        this.fullRepoName = fullRepoName;
        this.org = org;
        this.repo = repo;
    }

    public String getHost() {
        try {
            Map<String, Object> repo = MapUtils.getMap(data, REPO_NAME_KEY, Collections.emptyMap());
            String url = MapUtils.getString(repo, "git_url");
            return new URI(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    public String getFullRepoName() {
        return fullRepoName;
    }

    public String getOrg() {
        return org;
    }

    public String getRepo() {
        return repo;
    }

    public String getBranch() {
        if (PUSH_EVENT.equalsIgnoreCase(eventName)) {
            return getBranchPush(data);
        } else if (PULL_REQUEST_EVENT.equalsIgnoreCase(eventName)) {
            return getBranchPullRequest(data);
        }

        return null;
    }

    public String getSender() {
        Map<String, Object> sender = MapUtils.getMap(data, "sender", Collections.emptyMap());
        return MapUtils.getString(sender, "login");
    }

    public String getAction() {
        return getString("action");
    }

    public String getString(String key) {
        return MapUtils.getString(data, key);
    }

    public Map<String, Object> raw() {
        return data;
    }

    private static String getBranchPush(Map<String, Object> event) {
        String ref = MapUtils.getString(event, "ref");
        if (ref == null) {
            return null;
        }

        return GithubUtils.getRefShortName(ref);
    }

    private static String getBranchPullRequest(Map<String, Object> event) {
        Map<String, Object> pr = MapUtils.getMap(event, PULL_REQUEST_EVENT, Collections.emptyMap());
        Map<String, Object> base = MapUtils.getMap(pr, "base", Collections.emptyMap());
        return MapUtils.getString(base, "ref");
    }
}
