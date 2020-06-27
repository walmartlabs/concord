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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.GithubAuthenticatingFilter;
import com.walmartlabs.concord.server.security.github.GithubKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.repository.GitCliRepositoryProvider.DEFAULT_BRANCH;
import static com.walmartlabs.concord.server.events.github.Constants.*;

/**
 * Handles the V1 GitHub trigger definitions.
 *
 * @deprecated superseded by {@link GithubTriggerV2Processor}
 */
@Named
@Singleton
@Deprecated
public class GithubTriggerV1Processor implements GithubTriggerProcessor {

    private static final RepositoryItem UNKNOWN_REPO = new RepositoryItem(null, null, null);
    private static final int VERSION_ID = 1;

    private final RepositoryDao repositoryDao;
    private final TriggersDao triggersDao;
    private final GithubConfiguration githubCfg;
    private final GithubTriggerDefinitionEnricher triggerDefinitionEnricher;
    private final LoadingCache<UUID, ProjectEntry> projectCache;
    private final Histogram reposPerEvent;
    private final Histogram triggersPerEvent;
    private final Histogram filteredTriggersPerEvent;

    @Inject
    public GithubTriggerV1Processor(RepositoryDao repositoryDao,
                                    ProjectDao projectDao,
                                    TriggersDao triggersDao,
                                    GithubConfiguration githubCfg,
                                    GithubTriggerDefinitionEnricher triggerDefinitionEnricher,
                                    MetricRegistry metricRegistry) {

        this.repositoryDao = repositoryDao;
        this.triggersDao = triggersDao;
        this.githubCfg = githubCfg;
        this.triggerDefinitionEnricher = triggerDefinitionEnricher;

        this.projectCache = CacheBuilder.newBuilder()
                .expireAfterAccess(10, TimeUnit.SECONDS)
                .build(new CacheLoader<UUID, ProjectEntry>() {
                    @Override
                    public ProjectEntry load(UUID key) {
                        return projectDao.get(key);
                    }
                });

        this.reposPerEvent = metricRegistry.histogram("repositories-count-per-github-event-v1-processor");
        this.triggersPerEvent = metricRegistry.histogram("triggers-count-per-github-event-v1-processor");
        this.filteredTriggersPerEvent = metricRegistry.histogram("filtered-triggers-count-per-github-event-v1-processor");
    }

    @Override
    @WithTimer
    public void process(String eventName, Payload payload, UriInfo uriInfo, List<Result> result) {
        if (payload.getFullRepoName() == null) {
            return;
        }

        // support for hooks restricted to a specific repository
        GithubKey githubKey = GithubKey.getCurrent();
        UUID hookProjectId = githubKey.getProjectId();

        String eventBranch = payload.getBranch();
        List<RepositoryItem> repos = findRepos(payload.getFullRepoName(), eventBranch, hookProjectId);

        reposPerEvent.update(repos.size());

        boolean unknownRepo = repos.isEmpty();
        if (unknownRepo) {
            repos = Collections.singletonList(UNKNOWN_REPO);
        }

        for (RepositoryItem r : repos) {
            Map<String, Object> conditions = buildConditions(payload, r.repositoryName, eventBranch, r.project, eventName);
            conditions = enrich(conditions, uriInfo);

            Map<String, Object> triggerConditions = conditions;
            Map<String, Object> triggerEvent = buildTriggerEvent(payload, r.id, r.project, conditions);

            List<TriggerEntry> allTriggers = listTriggers(hookProjectId);
            triggersPerEvent.update(allTriggers.size());

            List<TriggerEntry> triggers = allTriggers.stream()
                    // skip empty push events if the trigger's configuration says so
                    .filter(t -> !(GithubUtils.ignoreEmptyPush(t) && GithubUtils.isEmptyPush(eventName, payload)))
                    .map(triggerDefinitionEnricher::enrich)
                    .filter(t -> DefaultEventFilter.filter(triggerConditions, t))
                    .collect(Collectors.toList());

            filteredTriggersPerEvent.update(triggers.size());

            if (!triggers.isEmpty()) {
                result.add(Result.from(triggerEvent, triggers));
            }
        }
    }

    @WithTimer
    List<TriggerEntry> listTriggers(UUID projectId) {
        return triggersDao.list(projectId, EVENT_SOURCE, VERSION_ID, null);
    }

    @WithTimer
    List<RepositoryItem> findRepos(String repoName, String branch, UUID hookProjectId) {
        return repositoryDao.find(hookProjectId, repoName).stream()
                .filter(r -> GithubUtils.isRepositoryUrl(repoName, r.getUrl(), githubCfg.getGithubDomain()))
                .filter(r -> isBranchEq(r.getBranch(), branch))
                .map(r -> {
                    ProjectEntry project = projectCache.getUnchecked(r.getProjectId());
                    return new RepositoryItem(r.getId(), project, r.getName());
                })
                .filter(r -> r.project != null)
                .collect(Collectors.toList());
    }

    private static boolean isBranchEq(String repoBranch, String eventBranch) {
        if (eventBranch == null) {
            return true;
        }

        if (repoBranch == null) {
            return DEFAULT_BRANCH.equals(eventBranch);
        }

        return repoBranch.equals(eventBranch);
    }

    private static Map<String, Object> enrich(Map<String, Object> event, UriInfo uriInfo) {
        if (uriInfo == null) {
            return event;
        }

        MultivaluedMap<String, String> qp = uriInfo.getQueryParameters();
        if (qp == null || qp.isEmpty()) {
            return event;
        }

        Map<String, Object> m = new HashMap<>(event);
        qp.keySet().forEach(k -> m.put(k, qp.getFirst(k)));

        m.remove(GithubAuthenticatingFilter.HOOK_PROJECT_ID);
        m.remove(GithubAuthenticatingFilter.HOOK_REPO_TOKEN);

        return m;
    }

    private static Map<String, Object> buildTriggerEvent(Payload payload,
                                                         UUID repoId,
                                                         ProjectEntry project,
                                                         Map<String, Object> conditions) {

        Map<String, Object> m = new HashMap<>();
        m.put(COMMIT_ID_KEY, payload.getString("after"));
        if (repoId != null) {
            m.put(REPO_ID_KEY, repoId);
        }
        m.putAll(conditions);
        if (project != null) {
            m.put(PROJECT_NAME_KEY, project.getName());
            m.put(ORG_NAME_KEY, project.getOrgName());
        } else {
            m.remove(PROJECT_NAME_KEY);
            m.remove(ORG_NAME_KEY);
        }

        m.put(PAYLOAD_KEY, payload.raw());

        return m;
    }

    private static Map<String, Object> buildConditions(Payload payload,
                                                       String repoName, String branch,
                                                       ProjectEntry project, String eventName) {
        Map<String, Object> result = new HashMap<>();

        if (project != null) {
            result.put(ORG_NAME_KEY, project.getOrgName());
            result.put(PROJECT_NAME_KEY, project.getName());
            result.put(REPO_NAME_KEY, repoName);
            result.put(UNKNOWN_REPO_KEY, false);
        } else {
            result.put(ORG_NAME_KEY, "n/a");
            result.put(PROJECT_NAME_KEY, "n/a");
            result.put(REPO_NAME_KEY, "n/a");
            result.put(UNKNOWN_REPO_KEY, true);
        }

        result.put(REPO_BRANCH_KEY, branch);
        result.put(AUTHOR_KEY, payload.getSender());
        result.put(TYPE_KEY, eventName);
        result.put(STATUS_KEY, payload.getAction());
        result.put(PAYLOAD_KEY, payload.raw());

        // match only with v1 triggers
        result.put(VERSION_KEY, VERSION_ID);

        return result;
    }

    private static class RepositoryItem {

        private final UUID id;

        private final String repositoryName;

        private final ProjectEntry project;

        public RepositoryItem(UUID id, ProjectEntry project, String repositoryName) {
            this.id = id;
            this.repositoryName = repositoryName;
            this.project = project;
        }
    }
}
