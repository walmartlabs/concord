package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.trigger.TriggerEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.PayloadParser;
import com.walmartlabs.concord.server.process.pipelines.processors.Pipeline;
import com.walmartlabs.concord.server.triggers.TriggersDao;
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

    protected void process(String eventId, Map<String, String> triggerConditions, Map<String, Object> triggerEvent) {
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
            return null;
        }

        try {
            projectPipeline.process(payload);
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}'] -> error starting the process ('{}')", projectName, repoName, flowName, instanceId, e);
            return null;
        }

        return instanceId;
    }

}
