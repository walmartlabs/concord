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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.MapUtils;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.events.github.Constants.*;

public class Payload {

    /**
     * List of supported repository-level events.
     */
    private static final Set<String> REPOSITORY_EVENTS = Set.of(
            "check_run",
            "check_suite",
            "code_scanning_alert",
            "commit_comment",
            "create",
            "delete",
            "dependabot_alert",
            "fork",
            "issue_comment",
            "issues",
            "label",
            "member",
            "pull_request",
            "pull_request_review",
            "pull_request_review_comment",
            "push",
            "release",
            "repository",
            "secret_scanning_alert",
            "secret_scanning_alert_location",
            "star",
            "status",
            "team",
            "team_add",
            "watch",
            "workflow_dispatch",
            "workflow_job",
            "workflow_run"
    );

    /**
     * List of supported organization-level events.
     */
    private static final Set<String> ORGANIZATION_EVENTS = Set.of(
            "membership",
            "organization",
            "org_block"
    );

    public static Payload from(String eventName, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        String fullRepoName = null;
        String org = null;
        String repo = null;

        if (REPOSITORY_EVENTS.contains(eventName)) {
            Map<String, Object> m = MapUtils.getMap(data, REPO_NAME_KEY, Map.of());
            fullRepoName = MapUtils.getString(m, "full_name");

            if (fullRepoName != null) {
                String[] as = fullRepoName.split("/");
                if (as.length < 2) {
                    return null;
                }

                org = as[0];
                repo = as[1];
            }
        } else if (ORGANIZATION_EVENTS.contains(eventName)) {
            Map<String, Object> m = MapUtils.getMap(data, ORGANIZATION_KEY, Map.of());
            org = MapUtils.getString(m, "login");
        } else {
            return null;
        }

        return new Payload(eventName, fullRepoName, org, repo, data);
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
            Map<String, Object> repository = MapUtils.getMap(data, REPO_NAME_KEY, Map.of());
            String url = MapUtils.getString(repository, "git_url");
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

    public String eventName() {
        return eventName;
    }

    public String getBranch() {
        switch (eventName.toLowerCase()) {
            case PUSH_EVENT:
            case "create":
            case "delete":
                return getRef(data);
            case PULL_REQUEST_EVENT:
            case "pull_request_review":
            case "pull_request_review_comment":
                return getBranchPullRequest(data);
            default:
                return null;
        }
    }

    public String getHead() {
        switch (eventName.toLowerCase()) {
            case PUSH_EVENT:
            case "create":
            case "delete":
                return getRef(data);
            case PULL_REQUEST_EVENT:
            case "pull_request_review":
            case "pull_request_review_comment":
                return getPullRequestHead(data);
            default:
                return null;
        }
    }

    public Map<String, Set<String>> getFiles() {
        if (!PUSH_EVENT.equalsIgnoreCase(eventName)) {
            return Map.of();
        }

        List<Map<String, Object>> commits = MapUtils.getList(data, "commits", List.of());
        Map<String, Set<String>> files = new HashMap<>();
        for (Map<String, Object> c : commits) {
            append(c, "added", files);
            append(c, "removed", files);
            append(c, "modified", files);
        }
        return files;
    }

    public boolean isPullRequestFromDifferentRepo() {
        Map<String, Object> pullRequest = getPullRequestAttribute(this.raw());
        String baseCloneUrl = getPullRequestCloneUrl(pullRequest, BASE_KEY);
        String headCloneUrl = getPullRequestCloneUrl(pullRequest, HEAD_KEY);

        return !Objects.equals(baseCloneUrl, headCloneUrl);
    }

    /**
     * @return <code>true</code> when event contains `pull_request` attribute.<br/>
     *         NOTE: this does <em>not</em> indicate the payload is from a <code>pull_request</code>
     *         event. It may be from another event related to a pull request such as
     *         <code>pull_request_review</code> or <code>pull_request_review_comment</code>
     */
    public boolean hasPullRequestEntry() {
        return raw().containsKey(PULL_REQUEST_EVENT);
    }

    public String getPullRequestBaseUrl() {
        Map<String, Object> pullRequest = getPullRequestAttribute(this.raw());
        return getPullRequestCloneUrl(pullRequest, BASE_KEY);
    }

    public String getPullRequestHeadUrl() {
        Map<String, Object> pullRequest = getPullRequestAttribute(this.raw());
        return getPullRequestCloneUrl(pullRequest, HEAD_KEY);
    }

    private static void append(Map<String, Object> c, String name, Map<String, Set<String>> result) {
        List<String> value = MapUtils.getList(c, name, List.of());
        result.compute(name, (k, v) -> (v == null) ? new HashSet<>(value) : Stream.concat(v.stream(), value.stream()).collect(Collectors.toSet()));
    }

    public String getSender() {
        Map<String, Object> sender = MapUtils.getMap(data, "sender", Map.of());
        return MapUtils.getString(sender, "login");
    }

    public String getSenderLdapDn() {
        Object result = ConfigurationUtils.get(data, "sender", "ldap_dn");
        if (result instanceof String s) {
            return s;
        }
        return null;
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

    private static String getRef(Map<String, Object> event) {
        String ref = MapUtils.getString(event, REF_KEY);
        if (ref == null) {
            return null;
        }

        return GithubUtils.getRefShortName(ref);
    }

    private static String getPullRequestHead(Map<String, Object> event) {
        Map<String, Object> pr = MapUtils.getMap(event, PULL_REQUEST_EVENT, Map.of());
        Map<String, Object> base = MapUtils.getMap(pr, HEAD_KEY, Map.of());
        return MapUtils.getString(base, REF_KEY);
    }

    private static String getBranchPullRequest(Map<String, Object> event) {
        Map<String, Object> pr = MapUtils.getMap(event, PULL_REQUEST_EVENT, Map.of());
        Map<String, Object> base = MapUtils.getMap(pr, BASE_KEY, Map.of());
        return MapUtils.getString(base, REF_KEY);
    }

    private static Map<String, Object> getPullRequestAttribute(Map<String, Object> event) {
        return MapUtils.getMap(event, PULL_REQUEST_EVENT, Map.of());
    }

    private static String getPullRequestCloneUrl(Map<String, Object> pullRequest, String baseOrHead) {
        Map<String, Object> head = MapUtils.getMap(pullRequest, baseOrHead, Map.of());
        Map<String, Object> headRepo = MapUtils.getMap(head, "repo", Map.of());
        return MapUtils.getString(headRepo, "clone_url", "");
    }

    @Override
    public String toString() {
        return "Payload{" +
                "eventName='" + eventName + '\'' +
                ", data=" + data +
                ", fullRepoName='" + fullRepoName + '\'' +
                ", org='" + org + '\'' +
                ", repo='" + repo + '\'' +
                '}';
    }
}
