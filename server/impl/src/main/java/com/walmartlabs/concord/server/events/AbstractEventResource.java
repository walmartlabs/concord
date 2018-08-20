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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class AbstractEventResource {

    private static final TriggerDefinitionEnricher AS_IS_ENRICHER = entry -> entry;

    private final Logger log;
    private final ProcessManager processManager;
    private final TriggersDao triggersDao;
    private final ProjectDao projectDao;
    private final TriggerDefinitionEnricher triggerDefinitionEnricher;
    private final TriggersConfiguration triggersCfg;

    public AbstractEventResource(ProcessManager processManager,
                                 TriggersDao triggersDao, ProjectDao projectDao, TriggersConfiguration triggersCfg) {
        this(processManager, triggersDao, projectDao, AS_IS_ENRICHER, triggersCfg);
    }

    public AbstractEventResource(ProcessManager processManager,
                                 TriggersDao triggersDao, ProjectDao projectDao,
                                 TriggerDefinitionEnricher enricher,
                                 TriggersConfiguration triggersCfg) {

        this.processManager = processManager;
        this.triggersDao = triggersDao;
        this.projectDao = projectDao;
        this.triggerDefinitionEnricher = enricher;
        this.log = LoggerFactory.getLogger(this.getClass());
        this.triggersCfg = triggersCfg;
    }

    protected int process(String eventId, String eventName, Map<String, Object> conditions, Map<String, Object> event) {
        List<TriggerEntry> triggers = triggersDao.list(eventName).stream()
                .map(triggerDefinitionEnricher::enrich)
                .filter(t -> filter(conditions, t))
                .collect(Collectors.toList());

        for (TriggerEntry t : triggers) {
            if (isDisabled(eventName)) {
                log.warn("process ['{}'] - disabled, skipping (triggered by {})", event, t);
                continue;
            }

            Map<String, Object> args = new HashMap<>();
            if (t.getArguments() != null) {
                args.putAll(t.getArguments());
            }
            args.put("event", event);

            try {
                UUID orgId = projectDao.getOrgId(t.getProjectId());
                UUID instanceId = startProcess(orgId, t.getProjectId(), t.getRepositoryId(), t.getEntryPoint(), t.getActiveProfiles(), args);
                log.info("process ['{}'] -> new process ('{}') triggered by {}", eventId, instanceId, t);
            } catch (Exception e) {
                log.error("process ['{}', '{}', '{}'] -> error", event, eventName, t.getId(), e);
            }
        }

        return triggers.size();
    }

    private boolean isDisabled(String eventName) {
        return triggersCfg.isDisableAll() || triggersCfg.getDisabled().contains(eventName);
    }

    private boolean filter(Map<String, Object> conditions, TriggerEntry t) {
        try {
            return EventMatcher.matches(conditions, t.getConditions());
        } catch (Exception e) {
            log.warn("filter [{}, {}] -> error while matching events: {}", conditions, t, e.getMessage());
            return false;
        }
    }

    private UUID startProcess(UUID orgId, UUID projectId, UUID repoId, String entryPoint, List<String> activeProfiles, Map<String, Object> args) throws IOException {
        UUID instanceId = UUID.randomUUID();

        UserPrincipal initiator = UserPrincipal.assertCurrent();

        Map<String, Object> request = new HashMap<>();
        if (activeProfiles != null) {
            request.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);
        }
        if (args != null) {
            request.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        Payload payload = new PayloadBuilder(instanceId)
                .initiator(initiator.getId(), initiator.getUsername())
                .organization(orgId)
                .project(projectId)
                .repository(repoId)
                .entryPoint(entryPoint)
                .configuration(request)
                .build();

        processManager.start(payload, false);
        return instanceId;
    }

    public interface TriggerDefinitionEnricher {
        TriggerEntry enrich(TriggerEntry entry);
    }
}
