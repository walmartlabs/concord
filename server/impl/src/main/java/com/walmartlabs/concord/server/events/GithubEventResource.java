package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.ProcessManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.*;

import static com.walmartlabs.concord.server.repository.CachedRepositoryManager.RepositoryCacheDao;
import static com.walmartlabs.concord.server.repository.RepositoryManager.DEFAULT_BRANCH;

@Named
@Singleton
@Api(value = "GitHub Events", authorizations = {})
@Path("/events/github")
public class GithubEventResource extends AbstractEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubEventResource.class);

    private static final String EVENT_SOURCE = "github";

    private static final String ORG_NAME_KEY = "org";
    private static final String REPO_ID_KEY = "repositoryId";
    private static final String REPO_NAME_KEY = "repository";
    private static final String PROJECT_NAME_KEY = "project";
    private static final String REPO_BRANCH_KEY = "branch";
    private static final String COMMIT_ID_KEY = "commitId";
    private static final String PUSHER_KEY = "author";

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final RepositoryCacheDao repositoryCacheDao;
    private final GithubWebhookManager webhookManager;

    @Inject
    public GithubEventResource(ProjectDao projectDao,
                               TriggersDao triggersDao,
                               RepositoryDao repositoryDao,
                               RepositoryCacheDao repositoryCacheDao,
                               ProcessManager processManager,
                               GithubWebhookManager webhookManager,
                               TriggersConfiguration triggersConfiguration) {

        super(processManager, triggersDao, projectDao, new ProjectOrgEnricher(projectDao), triggersConfiguration);

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.repositoryCacheDao = repositoryCacheDao;
        this.webhookManager = webhookManager;
    }

    @POST
    @ApiOperation("Handles GitHub push events")
    @Path("/push")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @WithTimer
    @SuppressWarnings("unchecked")
    public String push(@ApiParam Map<String, Object> event) {

        if (event == null) {
            return "ok";
        }

        Map<String, Object> repo = (Map<String, Object>) event.getOrDefault("repository", Collections.emptyMap());
        String repoName = (String) repo.get("full_name");
        if (repoName == null) {
            return "ok";
        }

        List<RepositoryEntry> repos = repositoryDao.find(repoName);
        if (repos.isEmpty()) {
            log.info("push ['{}'] -> repository not found, delete webhook", repoName);
            webhookManager.unregister(repoName);
            return "ok";
        }

        String eventBranch = getBranch(event);

        for (RepositoryEntry r : repos) {
            String rBranch = Optional.ofNullable(r.getBranch()).orElse(DEFAULT_BRANCH);
            if (!rBranch.equals(eventBranch)) {
                continue;
            }

            ProjectEntry project = projectDao.get(r.getProjectId());
            if (project == null) {
                log.warn("push ['{}'] -> project '{}' not found", repoName, r.getProjectId());
                continue;
            }

            repositoryCacheDao.updateLastPushDate(r.getId(), new Date());

            Map<String, Object> triggerConditions = buildConditions(r, event, project);
            Map<String, Object> triggerEvent = buildTriggerEvent(event, r, project, triggerConditions);

            String eventId = r.getId().toString();
            int count = process(eventId, EVENT_SOURCE, triggerConditions, triggerEvent);

            log.info("event ['{}', '{}', '{}'] -> {} processes started", eventId, triggerConditions, triggerEvent, count);
        }

        return "ok";
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         RepositoryEntry repo,
                                                         ProjectEntry project,
                                                         Map<String, Object> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put(COMMIT_ID_KEY, event.get("after"));
        result.put(REPO_ID_KEY, repo.getId());
        result.put(PROJECT_NAME_KEY, project.getName());
        result.put(ORG_NAME_KEY, project.getOrgName());
        result.putAll(conditions);
        return result;
    }

    private static String getBranch(Map<String, Object> event) {
        String ref = (String) event.get("ref");
        if (ref == null) {
            return null;
        }

        String[] refPath = ref.split("/");
        return refPath[refPath.length - 1];
    }

    private static Map<String, Object> buildConditions(RepositoryEntry repo, Map<String, Object> event, ProjectEntry project) {
        Map<String, Object> result = new HashMap<>();
        result.put(ORG_NAME_KEY, project.getOrgName());
        result.put(PROJECT_NAME_KEY, project.getName());
        result.put(REPO_NAME_KEY, repo.getName());
        result.put(REPO_BRANCH_KEY, Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH));
        result.put(PUSHER_KEY, getPusher(event));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String getPusher(Map<String, Object> event) {
        Map<String, Object> pusher = (Map<String, Object>) event.get("pusher");
        if (pusher == null) {
            return null;
        }

        return (String) pusher.get("name");
    }

    private static class ProjectOrgEnricher implements TriggerDefinitionEnricher {

        private final ProjectDao projectDao;

        private ProjectOrgEnricher(ProjectDao projectDao) {
            this.projectDao = projectDao;
        }

        @Override
        public TriggerEntry enrich(TriggerEntry entry) {
            Map<String, Object> conditions = entry.getConditions();
            if (conditions == null) {
                conditions = new HashMap<>();
            }

            conditions.computeIfAbsent(ORG_NAME_KEY, k -> {
                ProjectEntry e = projectDao.get(entry.getProjectId());
                if (e == null) {
                    return null;
                }
                return e.getOrgName();
            });
            conditions.computeIfAbsent(PROJECT_NAME_KEY, k -> entry.getProjectName());

            return new TriggerEntry(entry.getId(),
                    entry.getProjectId(),
                    entry.getProjectName(),
                    entry.getRepositoryId(),
                    entry.getRepositoryName(),
                    entry.getEventSource(),
                    entry.getEntryPoint(),
                    entry.getArguments(),
                    conditions);
        }
    }
}
