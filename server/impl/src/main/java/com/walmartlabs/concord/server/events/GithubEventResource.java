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

import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
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

    private static final String AUTHOR_KEY = "author";
    private static final String COMMIT_ID_KEY = "commitId";
    private static final String ORG_NAME_KEY = "org";
    private static final String PAYLOAD_KEY = "payload";
    private static final String PROJECT_NAME_KEY = "project";
    private static final String REPO_BRANCH_KEY = "branch";
    private static final String REPO_ID_KEY = "repositoryId";
    private static final String REPO_NAME_KEY = "repository";
    private static final String STATUS_KEY = "status";
    private static final String TYPE_KEY = "type";

    private static final String PULL_REQUEST_EVENT = "pull_request";
    private static final String PUSH_EVENT = "push";

    private static final String DEFAULT_EVENT_TYPE = "push";

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final RepositoryCacheDao repositoryCacheDao;
    private final GithubWebhookManager webhookManager;
    private final GithubConfiguration cfg;

    @Inject
    public GithubEventResource(ProjectDao projectDao,
                               TriggersDao triggersDao,
                               RepositoryDao repositoryDao,
                               RepositoryCacheDao repositoryCacheDao,
                               ProcessManager processManager,
                               GithubWebhookManager webhookManager,
                               TriggersConfiguration triggersConfiguration,
                               UserManager userManager,
                               LdapManager ldapManager,
                               GithubConfiguration cfg) {

        super(processManager, triggersDao, projectDao, new GithubTriggerDefinitionEnricher(projectDao, cfg), triggersConfiguration, userManager, ldapManager);

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.repositoryCacheDao = repositoryCacheDao;
        this.webhookManager = webhookManager;
        this.cfg = cfg;
    }

    @POST
    @ApiOperation("Handles GitHub repository level events")
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @WithTimer
    @SuppressWarnings("unchecked")
    public String onEvent(@ApiParam Map<String, Object> payload,
                          @HeaderParam("X-GitHub-Event") String eventName,
                          @Context UriInfo uriInfo) {

        if (payload == null) {
            return "ok";
        }

        Map<String, Object> repo = (Map<String, Object>) payload.getOrDefault(REPO_NAME_KEY, Collections.emptyMap());
        String repoName = (String) repo.get("full_name");
        if (repoName == null) {
            return "ok";
        }

        List<RepositoryEntry> repos = repositoryDao.find(repoName);
        if (repos.isEmpty()) {
            if (cfg.isAutoRemoveUnknownWebhooks()) {
                log.info("'onEvent ['{}'] -> repository '{}' not found, delete webhook", eventName, repoName);
                webhookManager.unregister(repoName);
            } else {
                log.warn("'onEvent ['{}'] -> repository '{}' not found", eventName, repoName);
            }
            return "ok";
        }

        String eventBranch = getBranch(payload, eventName);

        for (RepositoryEntry r : repos) {
            String rBranch = Optional.ofNullable(r.getBranch()).orElse(DEFAULT_BRANCH);
            if (!rBranch.equals(eventBranch)) {
                continue;
            }

            ProjectEntry project = projectDao.get(r.getProjectId());
            if (project == null) {
                log.warn("'{}' payload ['{}'] -> project '{}' not found", eventName, repoName, r.getProjectId());
                continue;
            }

            if (PUSH_EVENT.equalsIgnoreCase(eventName)) {
                repositoryCacheDao.updateLastPushDate(r.getId(), new Date());
            }

            Map<String, Object> conditions = buildConditions(r, payload, project, eventName);
            conditions = enrich(conditions, uriInfo);

            Map<String, Object> event = buildTriggerEvent(payload, r, project, conditions);

            String eventId = r.getId().toString();
            int count = process(eventId, EVENT_SOURCE, conditions, event);

            log.info("payload ['{}', '{}', '{}'] -> {} processes started", eventId, conditions, event, count);
        }

        return "ok";
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
        return m;
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> payload,
                                                         RepositoryEntry repo,
                                                         ProjectEntry project,
                                                         Map<String, Object> conditions) {

        Map<String, Object> m = new HashMap<>();
        m.put(COMMIT_ID_KEY, payload.get("after"));
        m.put(REPO_ID_KEY, repo.getId());
        m.put(PROJECT_NAME_KEY, project.getName());
        m.put(ORG_NAME_KEY, project.getOrgName());
        m.putAll(conditions);

        m.put(PAYLOAD_KEY, payload);

        return m;
    }

    private static String getBranch(Map<String, Object> event, String eventName) {
        if (PUSH_EVENT.equalsIgnoreCase(eventName)) {
            return getBranchPush(event);
        } else if (PULL_REQUEST_EVENT.equalsIgnoreCase(eventName)) {
            return getBranchPullRequest(event);
        }

        return null;
    }

    private static String getBranchPush(Map<String, Object> event) {
        String ref = (String) event.get("ref");
        if (ref == null) {
            return null;
        }

        String[] refPath = ref.split("/");

        return refPath[refPath.length - 1];
    }

    @SuppressWarnings("unchecked")
    private static String getBranchPullRequest(Map<String, Object> event) {
        Map<String, Object> pr = (Map<String, Object>) event.get(PULL_REQUEST_EVENT);
        if (pr == null) {
            return null;
        }

        Map<String, Object> base = (Map<String, Object>) pr.get("base");
        return (String) base.get("ref");
    }

    private static Map<String, Object> buildConditions(RepositoryEntry repo, Map<String, Object> event, ProjectEntry project, String eventName) {
        Map<String, Object> result = new HashMap<>();
        result.put(ORG_NAME_KEY, project.getOrgName());
        result.put(PROJECT_NAME_KEY, project.getName());
        result.put(REPO_NAME_KEY, repo.getName());
        result.put(REPO_BRANCH_KEY, Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH));
        result.put(AUTHOR_KEY, getSender(event));
        result.put(TYPE_KEY, eventName);
        result.put(STATUS_KEY, event.get("action"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String getSender(Map<String, Object> event) {
        Map<String, Object> sender = (Map<String, Object>) event.get("sender");
        if (sender == null) {
            return null;
        }

        return (String) sender.get("login");
    }

    private static class GithubTriggerDefinitionEnricher implements TriggerDefinitionEnricher {

        private final ProjectDao projectDao;
        private final GithubConfiguration cfg;

        private GithubTriggerDefinitionEnricher(ProjectDao projectDao, GithubConfiguration cfg) {
            this.projectDao = projectDao;
            this.cfg = cfg;
        }

        @Override
        public TriggerEntry enrich(TriggerEntry entry) {
            // note that the resulting conditions must be compatible with the system trigger definitions
            // see com/walmartlabs/concord/server/org/triggers/concord.yml

            Map<String, Object> conditions = new HashMap<>();

            // add default conditions from the cfg file
            if (cfg.getDefaultFilter() != null) {
                conditions.putAll(cfg.getDefaultFilter());
            }

            // add the trigger definition's conditions
            if (entry.getConditions() != null) {
                conditions.putAll(entry.getConditions());
            }

            // compute the additional filters
            conditions.computeIfAbsent(ORG_NAME_KEY, k -> {
                ProjectEntry e = projectDao.get(entry.getProjectId());
                if (e == null) {
                    return null;
                }
                return e.getOrgName();
            });
            conditions.computeIfAbsent(PROJECT_NAME_KEY, k -> entry.getProjectName());
            conditions.computeIfAbsent(REPO_NAME_KEY, k -> entry.getRepositoryName());

            // TODO remove once the documentation and existing triggers are updated
            conditions.putIfAbsent(TYPE_KEY, DEFAULT_EVENT_TYPE);

            return new TriggerEntry(entry.getId(),
                    entry.getProjectId(),
                    entry.getProjectName(),
                    entry.getRepositoryId(),
                    entry.getRepositoryName(),
                    entry.getEventSource(),
                    entry.getActiveProfiles(),
                    entry.getArguments(),
                    conditions,
                    entry.getCfg());
        }
    }
}
