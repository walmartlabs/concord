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
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
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
    private final UserManager userManager;
    private final LdapManager ldapManager;

    public AbstractEventResource(ProcessManager processManager,
                                 TriggersDao triggersDao,
                                 ProjectDao projectDao,
                                 TriggersConfiguration triggersCfg,
                                 UserManager userManager,
                                 LdapManager ldapManager) {

        this(processManager, triggersDao, projectDao, AS_IS_ENRICHER, triggersCfg, userManager, ldapManager);
    }

    public AbstractEventResource(ProcessManager processManager,
                                 TriggersDao triggersDao, ProjectDao projectDao,
                                 TriggerDefinitionEnricher enricher,
                                 TriggersConfiguration triggersCfg,
                                 UserManager userManager,
                                 LdapManager ldapManager) {

        this.processManager = processManager;
        this.triggersDao = triggersDao;
        this.projectDao = projectDao;
        this.triggerDefinitionEnricher = enricher;
        this.log = LoggerFactory.getLogger(this.getClass());
        this.triggersCfg = triggersCfg;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
    }

    protected int process(String eventId,
                          String eventName,
                          Map<String, Object> conditions,
                          Map<String, Object> event,
                          ProcessConfigurationEnricher cfgEnricher) {

        List<TriggerEntry> triggers = triggersDao.list(eventName).stream()
                .map(triggerDefinitionEnricher::enrich)
                .filter(t -> filter(conditions, t))
                .collect(Collectors.toList());

        for (TriggerEntry t : triggers) {
            if (isDisabled(eventName)) {
                log.warn("process ['{}'] - disabled, skipping (triggered by {})", eventId, t);
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

                PartialProcessKey processKey = startProcess(orgId, t.getProjectId(), t.getRepositoryId(), cfg, initiator);
                log.info("process ['{}'] -> new process ('{}') triggered by {}", eventId, processKey, t);
            } catch (Exception e) {
                log.error("process ['{}', '{}', '{}'] -> error", eventId, eventName, t.getId(), e);
            }
        }

        return triggers.size();
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

        String author = event.getOrDefault("author", "").toString();
        return getOrCreateUser(author);
    }

    private UserEntry getOrCreateUser(String author) {
        try {
            LdapPrincipal p = ldapManager.getPrincipal(author);
            if (p == null) {
                throw new ConcordApplicationException("User not found: " + author);
            }

            return userManager.getOrCreate(author, UserType.LDAP);
        } catch (NamingException e) {
            throw new ConcordApplicationException("Error while retrieving LDAP data: " + e.getMessage(), e);
        }
    }

    private boolean filter(Map<String, Object> conditions, TriggerEntry t) {
        try {
            return EventMatcher.matches(conditions, t.getConditions());
        } catch (Exception e) {
            log.warn("filter [{}, {}] -> error while matching events: {}", conditions, t, e.getMessage());
            return false;
        }
    }

    private PartialProcessKey startProcess(UUID orgId,
                              UUID projectId,
                              UUID repoId,
                              Map<String, Object> cfg,
                              UserEntry initiator) throws IOException {

        PartialProcessKey processKey = PartialProcessKey.create();

        Payload payload = PayloadBuilder.start(processKey)
                .initiator(initiator.getId(), initiator.getName())
                .organization(orgId)
                .project(projectId)
                .repository(repoId)
                .configuration(cfg)
                .build();

        processManager.start(payload, false);

        return processKey;
    }

    public interface TriggerDefinitionEnricher {
        TriggerEntry enrich(TriggerEntry entry);
    }

    public interface ProcessConfigurationEnricher {
        Map<String, Object> enrich(TriggerEntry t, Map<String, Object> cfg);
    }
}
