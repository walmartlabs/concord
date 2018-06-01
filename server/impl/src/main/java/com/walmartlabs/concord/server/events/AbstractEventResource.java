package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.api.org.trigger.TriggerEntry;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class AbstractEventResource {

    private final Logger log;

    private final ProcessManager processManager;
    private final TriggersDao triggersDao;
    private final ProjectDao projectDao;

    public AbstractEventResource(ProcessManager processManager,
                                 TriggersDao triggersDao, ProjectDao projectDao) {

        this.processManager = processManager;
        this.triggersDao = triggersDao;
        this.projectDao = projectDao;
        this.log = LoggerFactory.getLogger(this.getClass());
    }

    protected int process(String eventId, String eventName, Map<String, Object> conditions, Map<String, Object> event) {
        List<TriggerEntry> triggers = triggersDao.list(eventName).stream()
                .filter(t -> filter(conditions, t))
                .collect(Collectors.toList());

        for (TriggerEntry t : triggers) {
            Map<String, Object> args = new HashMap<>();
            if (t.getArguments() != null) {
                args.putAll(t.getArguments());
            }
            args.put("event", event);

            UUID orgId = projectDao.getOrgId(t.getProjectId());
            UUID instanceId = startProcess(orgId, t.getProjectId(), t.getRepositoryId(), t.getEntryPoint(), args);
            log.info("process ['{}'] -> new process ('{}') triggered by {}", eventId, instanceId, t);
        }

        return triggers.size();
    }

    private boolean filter(Map<String, Object> conditions, TriggerEntry t) {
        try {
            return EventMatcher.matches(conditions, t.getConditions());
        } catch (Exception e) {
            log.warn("filter [{}, {}] -> error while matching events: {}", conditions, t, e.getMessage());
            return false;
        }
    }

    private UUID startProcess(UUID orgId, UUID projectId, UUID repoId, String flowName, Map<String, Object> args) {
        UUID instanceId = UUID.randomUUID();

        String initiator = getInitiator();

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.Request.ARGUMENTS_KEY, args);

        Payload payload;
        try {
            payload = new PayloadBuilder(instanceId)
                    .initiator(initiator)
                    .organization(orgId)
                    .project(projectId)
                    .repository(repoId)
                    .entryPoint(flowName)
                    .configuration(request)
                    .build();
        } catch (ProcessException e) {
            log.error("startProcess ['{}', '{}', '{}', '{}'] -> error creating a payload", orgId, projectId, repoId, flowName, e);
            throw e;
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}'] -> error creating a payload", orgId, projectId, repoId, flowName, e);
            throw new ProcessException(instanceId, "Error while creating a payload: " + e.getMessage(), e);
        }

        processManager.start(payload, false);
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
