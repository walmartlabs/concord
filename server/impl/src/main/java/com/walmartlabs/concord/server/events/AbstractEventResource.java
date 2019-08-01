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

import com.walmartlabs.concord.common.MapMatcher;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractEventResource {

    private static final TriggerDefinitionEnricher AS_IS_ENRICHER = entry -> entry;

    private final Logger log;
    private final ExternalEventsConfiguration eventsCfg;
    private final ProcessManager processManager;
    private final TriggersDao triggersDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final TriggerDefinitionEnricher triggerDefinitionEnricher;
    private final TriggersConfiguration triggersCfg;
    private final UserManager userManager;
    private final ProcessSecurityContext processSecurityContext;

    public AbstractEventResource(ExternalEventsConfiguration eventsCfg,
                                 ProcessManager processManager,
                                 TriggersDao triggersDao,
                                 ProjectDao projectDao,
                                 RepositoryDao repositoryDao,
                                 TriggersConfiguration triggersCfg,
                                 UserManager userManager,
                                 ProcessSecurityContext processSecurityContext) {

        this(eventsCfg, processManager, triggersDao, projectDao, repositoryDao, AS_IS_ENRICHER, triggersCfg, userManager, processSecurityContext);
    }

    public AbstractEventResource(ExternalEventsConfiguration eventsCfg,
                                 ProcessManager processManager,
                                 TriggersDao triggersDao, ProjectDao projectDao,
                                 RepositoryDao repositoryDao,
                                 TriggerDefinitionEnricher enricher,
                                 TriggersConfiguration triggersCfg,
                                 UserManager userManager,
                                 ProcessSecurityContext processSecurityContext) {

        this.eventsCfg = eventsCfg;
        this.processManager = processManager;
        this.triggersDao = triggersDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.triggerDefinitionEnricher = enricher;
        this.processSecurityContext = processSecurityContext;
        this.triggersCfg = triggersCfg;
        this.userManager = userManager;

        this.log = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Processes the event and starts new processes with triggers that are
     * matching the specified event.
     *
     * @return a list of IDs of started processes
     */
    protected List<PartialProcessKey> process(String eventId,
                                              String eventName,
                                              Map<String, Object> conditions,
                                              Map<String, Object> event,
                                              ProcessConfigurationEnricher cfgEnricher) {

        assertRoles(eventName);

        List<TriggerEntry> triggers = triggersDao.list(eventName).stream()
                .map(triggerDefinitionEnricher::enrich)
                .filter(t -> filter(conditions, t))
                .collect(Collectors.toList());

        List<PartialProcessKey> processKeys = new ArrayList<>(triggers.size());

        for (TriggerEntry t : triggers) {
            if (isDisabled(eventName)) {
                log.warn("process ['{}'] - disabled, skipping (triggered by {})", eventId, t);
                continue;
            }

            if (isRepositoryDisabled(t)) {
                log.warn("Repository is disabled, skipping -> ['{}'] ", t);
                continue;
            }

            Map<String, Object> args = new HashMap<>();
            if (t.getArguments() != null) {
                args.putAll(t.getArguments());
            }
            args.put("event", event);

            Map<String, Object> cfg = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);

            if (t.getEntryPoint() != null) {
                cfg.put(Constants.Request.ENTRY_POINT_KEY, t.getEntryPoint());
            }

            if (t.getActiveProfiles() != null) {
                cfg.put(Constants.Request.ACTIVE_PROFILES_KEY, t.getActiveProfiles());
            }

            if (cfgEnricher != null) {
                cfg = cfgEnricher.enrich(t, cfg);
            }

            try {
                UserEntry initiator = getInitiator(t, event);
                UUID orgId = projectDao.getOrgId(t.getProjectId());

                PartialProcessKey pk = startProcess(eventId, orgId, t, cfg, initiator);
                processKeys.add(pk);
                log.info("process ['{}'] -> new process ('{}') triggered by {}", eventId, pk, t);
            } catch (Exception e) {
                log.error("process ['{}', '{}', '{}'] -> error", eventId, eventName, t.getId(), e);
            }
        }

        return processKeys;
    }

    private boolean isRepositoryDisabled(TriggerEntry t) {
        return repositoryDao.get(t.getRepositoryId()).isDisabled();
    }

    private void assertRoles(String eventName) {
        // optional feature: require a specific user role to access the external events endpoint
        Map<String, String> requiredRoles = eventsCfg.getRequiredRoles();
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return;
        }

        Subject s = SecurityUtils.getSubject();

        requiredRoles.forEach((k, v) -> {
            if (eventName.matches(k) && !s.hasRole(v)) {
                throw new ConcordApplicationException("'" + v + "' role is required", Response.Status.FORBIDDEN);
            }
        });
    }

    private boolean isDisabled(String eventName) {
        return triggersCfg.isDisableAll() || triggersCfg.getDisabled().contains(eventName);
    }

    private UserEntry getInitiator(TriggerEntry trigger, Map<String, Object> event) {
        boolean isUseInitiator = trigger.isUseInitiator();
        if (!isUseInitiator) {
            UserPrincipal initiator = UserPrincipal.assertCurrent();
            return initiator.getUser();
        }

        return getOrCreateUserEntry(event);
    }

    protected UserEntry getOrCreateUserEntry(Map<String, Object> event) {
        // TODO make sure all event resources perform the user lookup correctly, e.g. using correct input data
        String author = event.getOrDefault("author", "").toString();
        return userManager.getOrCreate(author, null, UserType.LDAP);
    }

    private boolean filter(Map<String, Object> conditions, TriggerEntry t) {
        try {
            return MapMatcher.matches(conditions, t.getConditions());
        } catch (Exception e) {
            log.warn("filter [{}, {}] -> error while matching events: {}", conditions, t, e.getMessage());
            return false;
        }
    }

    private PartialProcessKey startProcess(String eventId,
                                           UUID orgId,
                                           TriggerEntry t,
                                           Map<String, Object> cfg,
                                           UserEntry initiator) throws Exception {

        PartialProcessKey processKey = PartialProcessKey.create();

        processSecurityContext.runAs(initiator.getId(), () -> {
            Payload payload = PayloadBuilder.start(processKey)
                    .initiator(initiator.getId(), initiator.getName())
                    .organization(orgId)
                    .project(t.getProjectId())
                    .repository(t.getRepositoryId())
                    .configuration(cfg)
                    .exclusiveGroup(t.getExclusiveGroup())
                    .triggeredBy(TriggeredByEntry.builder()
                            .externalEventId(eventId)
                            .trigger(t)
                            .build())
                    .build();

            processManager.start(payload);
            return null;
        });

        return processKey;
    }

    public interface TriggerDefinitionEnricher {
        TriggerEntry enrich(TriggerEntry entry);
    }

    public interface ProcessConfigurationEnricher {
        Map<String, Object> enrich(TriggerEntry t, Map<String, Object> cfg);
    }
}
