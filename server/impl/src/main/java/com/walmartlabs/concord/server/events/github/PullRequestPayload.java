package com.walmartlabs.concord.server.events.github;

import com.walmartlabs.concord.sdk.MapUtils;

import java.util.Map;
import java.util.Objects;

public class PullRequestPayload extends Payload {

    private static final String PULL_REQUEST = "pull_request";
    private static final String BASE = "base";
    private static final String HEAD = "head";

    protected PullRequestPayload(String eventName, String fullRepoName, String org, String repo, Map<String, Object> data) {
        super(eventName, fullRepoName, org, repo, data);
    }

    public boolean isPullRequestFromSameRepo() {
        Map<String, Object> pullRequest = getPullRequestAttribute();
        String baseCloneUrl = getCloneUrl(pullRequest, BASE);
        String headCloneUrl = getCloneUrl(pullRequest, HEAD);

        return Objects.equals(baseCloneUrl, headCloneUrl);
    }

    public String getBaseRepoUrl() {
        Map<String, Object> pullRequest = MapUtils.getMap(this.raw(), PULL_REQUEST, Map.of());
        return getCloneUrl(pullRequest, BASE);
    }

    public String getHeadRepoUrl() {
        Map<String, Object> pullRequest = MapUtils.getMap(this.raw(), PULL_REQUEST, Map.of());
        return getCloneUrl(pullRequest, HEAD);
    }

    private Map<String, Object> getPullRequestAttribute() {
        return MapUtils.getMap(this.raw(), PULL_REQUEST, Map.of());
    }

    private static String getCloneUrl(Map<String, Object> pullRequest, String baseOrHead) {
        Map<String, Object> head = MapUtils.getMap(pullRequest, baseOrHead, Map.of());
        Map<String, Object> headRepo = MapUtils.getMap(head, "repo", Map.of());
        return MapUtils.getString(headRepo, "clone_url", "");
    }

}
