package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.trigger.TriggerEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.PayloadParser;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.Pipeline;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.triggers.TriggersDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractEventResource {

    private final Logger log;

    private final String eventName;
    private final PayloadManager payloadManager;
    private final Pipeline projectPipeline;
    private final TriggersDao triggersDao;

    public AbstractEventResource(String eventName,
                                 PayloadManager payloadManager,
                                 Pipeline projectPipeline,
                                 TriggersDao triggersDao) {
        this.eventName = eventName;
        this.payloadManager = payloadManager;
        this.projectPipeline = projectPipeline;
        this.triggersDao = triggersDao;
        this.log = LoggerFactory.getLogger(this.getClass());
    }

    protected int process(String eventId, Map<String, String> triggerConditions, Map<String, Object> triggerEvent) {
        List<TriggerEntry> triggers = triggersDao.list(eventName, triggerConditions);
        for (TriggerEntry t : triggers) {
            Map<String, Object> processArgs = new HashMap<>();
            if (t.getArguments() != null) {
                processArgs.putAll(t.getArguments());
            }
            processArgs.put("event", triggerEvent);
            UUID instanceId = startProcess(t.getProjectName(), t.getRepositoryName(), t.getEntryPoint(), processArgs);
            log.info("process ['{}'] -> instanceId '{}'", eventId, instanceId);
        }
        return triggers.size();
    }

    private UUID startProcess(String projectName, String repoName, String flowName, Map<String, Object> args) {
        UUID instanceId = UUID.randomUUID();

        String initiator = getInitiator();

        PayloadParser.EntryPoint ep = new PayloadParser.EntryPoint(projectName, repoName, flowName);
        Map<String, Object> request = new HashMap<>();
        request.put(InternalConstants.Request.ARGUMENTS_KEY, args);

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, null, initiator, ep, request, null);
        } catch (ProcessException e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error creating a payload", projectName, repoName, flowName, e);
            throw e;
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error creating a payload", projectName, repoName, flowName, e);
            throw new ProcessException(instanceId, "Error while creating a payload: " + e.getMessage(), e);
        }

        try {
            projectPipeline.process(payload);
        } catch (ProcessException e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error starting the process {}", projectName, repoName, flowName, instanceId, e);
            throw e;
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error starting the process {}", projectName, repoName, flowName, instanceId, e);
            throw new ProcessException(instanceId, "Error while starting a process: " + e.getMessage(), e);
        }

        return instanceId;
    }

    private static String getInitiator() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null || !subject.isAuthenticated()) {
            return null;
        }

        UserPrincipal p = (UserPrincipal) subject.getPrincipal();
        return p != null ? p.getUsername() : null;
    }
}
