package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.trigger.TriggerEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.PayloadParser;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.ProjectPipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Pipeline;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.triggers.TriggersDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.*;

import static com.walmartlabs.concord.server.repository.CachedRepositoryManager.RepositoryCacheDao;
import static com.walmartlabs.concord.server.repository.RepositoryManager.DEFAULT_BRANCH;

@Named
public class GithubCallbackResourceImpl implements GithubCallbackResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubCallbackResourceImpl.class);

    private static final String EVENT_NAME = "github";

    private static final String REPO_ID_KEY = "repositoryId";
    private static final String REPO_NAME_KEY = "repository";
    private static final String PROJECT_NAME_KEY = "project";
    private static final String REPO_BRANCH_KEY = "branch";
    private static final String COMMIT_ID_KEY = "commitId";
    private static final String PUSHER_KEY = "author";

    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;
    private final RepositoryDao repositoryDao;
    private final RepositoryCacheDao repositoryCacheDao;
    private final PayloadManager payloadManager;
    private final Pipeline projectPipeline;

    @Inject
    public GithubCallbackResourceImpl(ProjectDao projectDao,
                                      TriggersDao triggersDao,
                                      RepositoryDao repositoryDao,
                                      RepositoryCacheDao repositoryCacheDao,
                                      PayloadManager payloadManager,
                                      ProjectPipeline projectPipeline) {
        this.projectDao = projectDao;
        this.triggersDao = triggersDao;
        this.repositoryDao = repositoryDao;
        this.repositoryCacheDao = repositoryCacheDao;
        this.payloadManager = payloadManager;
        this.projectPipeline = projectPipeline;
    }

    @Override
    public String push(UUID projectId, UUID repoId, Map<String, Object> event) {
        if (event == null) {
            return "ok";
        }

        String eventBranch = getBranch(event);
        RepositoryEntry repo = repositoryDao.get(projectId, repoId);
        if (repo == null) {
            log.warn("push ['{}', '{}', '{}'] -> repo not found", projectId, repoId, eventBranch);
            return "ok";
        }

        ProjectEntry project = projectDao.get(projectId);

        String repoBranch = Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH);
        if (!eventBranch.equals(repoBranch)) {
            log.info("push ['{}', '{}', '{}'] -> ignore, expected branch '{}'", project, repoId, eventBranch, repoBranch);
            return "ok";
        }

        repositoryCacheDao.updateLastPushDate(repoId, new Date());

        Map<String, String> triggerConditions = buildConditions(repo, event);
        Map<String, Object> triggerEvent = buildTriggerEvent(event, repo, project, triggerConditions);

        List<TriggerEntry> triggers = triggersDao.list(EVENT_NAME, triggerConditions);
        for (TriggerEntry t : triggers) {
            Map<String, Object> processArgs = new HashMap<>();
            if (t.getArguments() != null) {
                processArgs.putAll(t.getArguments());
            }
            processArgs.put("event", triggerEvent);
            UUID instanceId = startProcess(t.getProjectName(), t.getRepositoryName(), t.getEntryPoint(), processArgs);
            log.info("push ['{}', '{}'] -> process '{}'", projectId, repoId, instanceId);
        }

        log.info("push ['{}', '{}', '{}'] -> done", projectId, repoId, triggerEvent);

        return "ok";
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         RepositoryEntry repo,
                                                         ProjectEntry project,
                                                         Map<String, String> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put(COMMIT_ID_KEY, event.get("after"));
        result.put(REPO_ID_KEY, repo.getId());
        result.put(PROJECT_NAME_KEY, project.getName());
        result.putAll(conditions);
        return result;
    }

    private static String getBranch(Map<String, Object> event) {
        String ref = (String) event.get("ref");
        String[] refPath = ref.split("/");
        return refPath[refPath.length - 1];
    }

    private static Map<String, String> buildConditions(RepositoryEntry repo, Map<String, Object> event) {
        Map<String, String> result = new HashMap<>();
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

        return (String)pusher.get("name");
    }

    private UUID startProcess(String projectName, String repoName, String flowName, Map<String, Object> args) {
        UUID instanceId = UUID.randomUUID();

        PayloadParser.EntryPoint ep = new PayloadParser.EntryPoint(projectName, repoName, flowName);
        Map<String, Object> request = new HashMap<>();
        request.put(InternalConstants.Request.ARGUMENTS_KEY, args);

        Payload payload;
        try {

            payload = payloadManager.createPayload(instanceId, null, null, ep, request, null);
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error creating a payload", projectName, repoName, flowName, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        try {
            projectPipeline.process(payload);
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error starting the process ('{}')", projectName, repoName, flowName, instanceId, e);
            throw new ProcessException(instanceId, "Error starting the process", e, Response.Status.INTERNAL_SERVER_ERROR);
        }

        return instanceId;
    }
}