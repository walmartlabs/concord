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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.Constants.Trigger;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.cfg.GitHubConfiguration;
import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerUtils;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.github.GithubKey;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.events.github.Constants.*;

public class GithubTriggerProcessor {

    private static final int VERSION_ID = 2;
    private static final Logger log = LoggerFactory.getLogger(GithubTriggerProcessor.class);

    private final Dao dao;
    private final Set<EventEnricher> eventEnrichers;
    private final boolean isDisableReposOnDeletedRef;

    @Inject
    public GithubTriggerProcessor(Dao dao,
                                  Set<EventEnricher> eventEnrichers,
                                  GitHubConfiguration githubCfg) {
        this.dao = dao;
        this.eventEnrichers = eventEnrichers;
        this.isDisableReposOnDeletedRef = githubCfg.isDisableReposOnDeletedRef();
    }

    @WithTimer
    public void process(String eventName, Payload payload, UriInfo uriInfo, List<Result> result) {
        GithubKey githubKey = GithubKey.getCurrent();
        UUID projectId = githubKey.getProjectId();

        if (isDisableReposOnDeletedRef
            && PUSH_EVENT.equals(payload.eventName())
            && isRefDeleted(payload)) {

            List<RepositoryEntry> repositories = dao.findRepos(payload.getFullRepoName());
            // disable repos configured with the event branch
            repositories.stream()
                    .filter(r -> !r.isDisabled() && null != r.getBranch())
                    .filter(r -> r.getBranch().equals(payload.getBranch()))
                    .forEach(r -> disableRepo(r, payload));
        }

        List<TriggerEntry> triggers = listTriggers(projectId, payload.getOrg(), payload.getRepo());
        for (TriggerEntry t : triggers) {
            if (skipTrigger(t, eventName, payload)) {
                continue;
            }

            Map<String, Object> event = buildEvent(eventName, uriInfo, payload);
            enrichEventConditions(payload, t, event);

            if (DefaultEventFilter.filter(event, t)) {
                result.add(new Result(event, t));
            }
        }
    }

    static boolean skipTrigger(TriggerEntry t, String eventName, Payload payload) {
        // skip empty push events if the trigger's configuration says so
        if (GithubUtils.ignoreEmptyPush(t) && GithubUtils.isEmptyPush(eventName, payload)) {
            return true;
        }

        // process is destined to fail if attempted to start from commit in another repo
        // on an event from a pull request.
        if (TriggerUtils.isUseEventCommitId(t)
            && payload.hasPullRequestEntry()
            && payload.isPullRequestFromDifferentRepo()) {

            log.info("Skip start from {} event [{}, {}] -> Commit is in a different repository.",
                    eventName, payload.getPullRequestBaseUrl(), payload.getPullRequestHeadUrl());

            return true;
        }

        return false;
    }

    private void enrichEventConditions(Payload payload, TriggerEntry trigger, Map<String, Object> result) {
        for (EventEnricher e : eventEnrichers) {
            e.enrich(payload, trigger, result);
        }
    }

    private void disableRepo(RepositoryEntry repo, Payload payload) {
        log.info("disable repo ['{}', '{}'] -> ref deleted", repo.getId(), payload.getBranch());
        dao.disable(repo.getProjectId(), repo.getId());
    }

    private static boolean isRefDeleted(Payload payload) {
        Object val = payload.raw().get("deleted");

        if (val == null) {
            return false;
        }

        if (val instanceof String str) {
            return Boolean.parseBoolean(str);
        }

        return Boolean.TRUE.equals(val);
    }

    @WithTimer
    List<TriggerEntry> listTriggers(UUID projectId, String org, String repo) {
        return dao.listTriggers(projectId, org, repo);
    }

    private Map<String, Object> buildEvent(String eventName, UriInfo uriInfo, Payload payload) {
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
        result.put(QUERY_PARAMS_KEY, new HashMap<>(uriInfo.getQueryParameters()));

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

    public interface EventEnricher {

        void enrich(Payload payload, TriggerEntry trigger, Map<String, Object> result);
    }

    /**
     * Adds {@link Trigger#REPOSITORY_INFO} property to the event, but only if
     * the trigger's conditions contained the clause with the same key.
     */
    public static class RepositoryInfoEnricher implements EventEnricher {

        private final Dao dao;

        @Inject
        public RepositoryInfoEnricher(Dao dao) {
            this.dao = dao;
        }

        @Override
        @WithTimer
        public void enrich(Payload payload, TriggerEntry trigger, Map<String, Object> result) {
            Object projectInfoConditions = trigger.getConditions().get(Trigger.REPOSITORY_INFO);
            if (projectInfoConditions == null || payload.getFullRepoName() == null) {
                return;
            }

            List<Map<String, Object>> repositoryInfos = new ArrayList<>();
            List<RepositoryEntry> repositories = dao.findRepos(payload.getFullRepoName());

            for (RepositoryEntry r : repositories) {
                if (r.isDisabled()) {
                    continue;
                }

                Map<String, Object> repositoryInfo = new HashMap<>();
                repositoryInfo.put(REPO_ID_KEY, r.getId());
                repositoryInfo.put(REPO_NAME_KEY, r.getName());
                repositoryInfo.put(PROJECT_ID_KEY, r.getProjectId());
                if (r.getBranch() != null) {
                    repositoryInfo.put(REPO_BRANCH_KEY, r.getBranch());
                }
                repositoryInfo.put(REPO_ENABLED_KEY, !r.isDisabled());

                repositoryInfos.add(repositoryInfo);
            }

            if (!repositoryInfos.isEmpty()) {
                result.put(Trigger.REPOSITORY_INFO, repositoryInfos);
            }
        }
    }

    public static class Dao {
        private final RepositoryDao repoDao;
        private final TriggersDao triggersDao;
        private final Configuration cfg;

        @Inject
        public Dao(@MainDB Configuration cfg,
                   RepositoryDao repoDao,
                   TriggersDao triggersDao) {

            this.cfg = cfg;
            this.triggersDao = triggersDao;
            this.repoDao = repoDao;
        }

        private List<RepositoryEntry> findRepos(String repoOrgAndName) {
            String sshAndHttpPattern = "%[/:]" + repoOrgAndName + "(.git)?/?";
            return repoDao.findSimilar(sshAndHttpPattern);
        }

        List<TriggerEntry> listTriggers(UUID projectId, String org, String repo) {
            Map<String, String> conditions = new HashMap<>();

            if (org != null) {
                conditions.put(GITHUB_ORG_KEY, org);
            }

            if (repo != null) {
                conditions.put(GITHUB_REPO_KEY, repo);
            }

            return triggersDao.list(projectId, EVENT_SOURCE, VERSION_ID, conditions);
        }

        void disable(UUID projectId, UUID repoId) {
            tx(tx -> {
                repoDao.disable(tx, repoId);
                triggersDao.delete(tx, projectId, repoId);
            });
        }

        private DSLContext dsl() {
            return DSL.using(cfg);
        }

        private void tx(Consumer<DSLContext> c) {
            dsl().transaction(localCfg -> {
                DSLContext tx = DSL.using(localCfg);
                c.accept(tx);
            });
        }
    }

    public record Result(Map<String, Object> event, List<TriggerEntry> triggers) {

        private Result(Map<String, Object> event, TriggerEntry trigger) {
            this(event, List.of(trigger));
        }
    }
}
