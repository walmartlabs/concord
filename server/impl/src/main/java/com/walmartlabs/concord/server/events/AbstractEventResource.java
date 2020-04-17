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

import com.google.common.util.concurrent.SettableFuture;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerUtils;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
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
import java.util.concurrent.*;
import java.util.stream.Collectors;

public abstract class AbstractEventResource {

    private final Logger log;
    private final ExecutorService executor;

    private final ExternalEventsConfiguration eventsCfg;
    private final ProcessManager processManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final TriggersConfiguration triggersCfg;
    private final UserManager userManager;
    private final ProcessSecurityContext processSecurityContext;

    public AbstractEventResource(ExternalEventsConfiguration eventsCfg,
                                 ProcessManager processManager,
                                 ProjectDao projectDao,
                                 RepositoryDao repositoryDao,
                                 TriggersConfiguration triggersCfg,
                                 UserManager userManager,
                                 ProcessSecurityContext processSecurityContext) {

        this.eventsCfg = eventsCfg;
        this.processManager = processManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.processSecurityContext = processSecurityContext;
        this.triggersCfg = triggersCfg;
        this.userManager = userManager;

        this.log = LoggerFactory.getLogger(this.getClass());
        this.executor = createExecutor(eventsCfg.getWorkerThreads());

    }

    protected List<PartialProcessKey> process(String eventId,
                                              String eventName,
                                              Map<String, Object> event,
                                              List<TriggerEntry> triggers,
                                              ProcessConfigurationEnricher cfgEnricher) {
        if (isDisabled(eventName)) {
            log.warn("process ['{}'] event '{}' disabled", eventId, eventName);
            return Collections.emptyList();
        }

        assertRoles(eventName);

        return triggers.stream()
                .filter(t -> !isRepositoryDisabled(t))
                .map(t -> process(eventId, eventName, t, event, cfgEnricher))
                .collect(Collectors.toList()) // collect all "futures"
                .stream()
                .map(AbstractEventResource::resolve)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Future<PartialProcessKey> process(String eventId,
                                              String eventName,
                                              TriggerEntry t,
                                              Map<String, Object> event,
                                              ProcessConfigurationEnricher cfgEnricher) {

        UserEntry initiator;
        try {
            initiator = getInitiator(t, event);
        } catch (Exception e) {
            log.error("process ['{}', '{}', '{}'] -> error", eventId, eventName, t.getId(), e);
            SettableFuture<PartialProcessKey> f = SettableFuture.create();
            f.set(null);
            return f;
        }

        return executor.submit(() -> {
            Map<String, Object> args = new HashMap<>();
            if (t.getArguments() != null) {
                args.putAll(t.getArguments());
            }
            args.put("event", ExpressionUtils.escapeMap(event));

            Map<String, Object> cfg = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);

            if (TriggerUtils.getEntryPoint(t) != null) {
                cfg.put(Constants.Request.ENTRY_POINT_KEY, TriggerUtils.getEntryPoint(t));
            }

            if (t.getActiveProfiles() != null) {
                cfg.put(Constants.Request.ACTIVE_PROFILES_KEY, t.getActiveProfiles());
            }

            cfg.put(Constants.Request.EXCLUSIVE, TriggerUtils.getExclusive(t));

            if (cfgEnricher != null) {
                cfg = cfgEnricher.enrich(t, cfg);
            }

            try {
                UUID orgId = projectDao.getOrgId(t.getProjectId());

                PartialProcessKey pk = startProcess(eventId, orgId, t, cfg, initiator);
                log.info("process ['{}'] -> new process ('{}') triggered by {}", eventId, pk, t);
                return pk;
            } catch (Exception e) {
                log.error("process ['{}', '{}', '{}'] -> error", eventId, eventName, t.getId(), e);
                return null;
            }
        });
    }

    private boolean isRepositoryDisabled(TriggerEntry t) {
        return repositoryDao.get(t.getRepositoryId()).isDisabled();
    }

    private void assertRoles(String eventName) {
        if (Roles.isAdmin()) {
            return;
        }

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

    protected boolean isDisabled(String eventName) {
        return triggersCfg.isDisableAll() || triggersCfg.getDisabled().contains(eventName);
    }

    private UserEntry getInitiator(TriggerEntry trigger, Map<String, Object> event) {
        boolean isUseInitiator = TriggerUtils.isUseInitiator(trigger);
        if (!isUseInitiator) {
            UserPrincipal initiator = UserPrincipal.assertCurrent();
            return initiator.getUser();
        }

        return getOrCreateUserEntry(event);
    }

    protected UserEntry getOrCreateUserEntry(Map<String, Object> event) {
        // TODO make sure all event resources perform the user lookup correctly, e.g. using correct input data
        String author = event.getOrDefault("author", "").toString();
        return userManager.getOrCreate(author, null, UserType.LDAP)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + author));
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

    private static <T> T resolve(Future<T> f) {
        try {
            return f.get();
        } catch (InterruptedException | ExecutionException e) { // NOSONAR
            throw new RuntimeException(e);
        }
    }

    private static ExecutorService createExecutor(int poolSize) {
        ThreadPoolExecutor p = new ThreadPoolExecutor(1, poolSize, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
        p.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return p;
    }

    public interface ProcessConfigurationEnricher {
        Map<String, Object> enrich(TriggerEntry t, Map<String, Object> cfg);
    }
}
