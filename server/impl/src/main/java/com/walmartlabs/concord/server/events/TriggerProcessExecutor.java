package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.google.inject.Inject;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerUtils;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Named
public class TriggerProcessExecutor {

    public interface ProcessConfigurationEnricher {

        Map<String, Object> enrich(TriggerEntry t, Map<String, Object> cfg);
    }

    public interface TriggerExclusiveParamsResolver {

        Map<String, Object> resolve(TriggerEntry t);
    }

    private static final Logger log = LoggerFactory.getLogger(TriggerProcessExecutor.class);

    private final ExternalEventsConfiguration eventsCfg;
    private final TriggersConfiguration triggersCfg;
    private final ProcessManager processManager;
    private final RepositoryDao repositoryDao;
    private final ProjectDao projectDao;
    private final ProcessSecurityContext processSecurityContext;
    private final ExecutorService executor;

    @Inject
    public TriggerProcessExecutor(ExternalEventsConfiguration eventsCfg,
                                  TriggersConfiguration triggersCfg,
                                  ProcessManager processManager,
                                  RepositoryDao repositoryDao,
                                  ProjectDao projectDao,
                                  ProcessSecurityContext processSecurityContext) {

        this.eventsCfg = eventsCfg;
        this.triggersCfg = triggersCfg;
        this.processManager = processManager;
        this.repositoryDao = repositoryDao;
        this.projectDao = projectDao;
        this.processSecurityContext = processSecurityContext;
        this.executor = createExecutor(eventsCfg.getWorkerThreads());
    }

    public boolean isDisabled(String eventName) {
        return triggersCfg.isDisableAll() || triggersCfg.getDisabled().contains(eventName);
    }

    public List<PartialProcessKey> execute(Event event,
                                           TriggerEventInitiatorResolver initiatorResolver,
                                           List<TriggerEntry> triggers) {

        return execute(event, triggers, initiatorResolver, null, TriggerUtils::getExclusive);
    }

    @WithTimer
    public List<PartialProcessKey> execute(Event event,
                                           List<TriggerEntry> triggers,
                                           TriggerEventInitiatorResolver initiatorResolver,
                                           ProcessConfigurationEnricher cfgEnricher,
                                           TriggerExclusiveParamsResolver exclusiveResolver) {

        if (isDisabled(event.name())) {
            log.warn("process ['{}'] event '{}' disabled", event.id(), event.name());
            return Collections.emptyList();
        }

        assertRoles(event.name());

        return triggers.stream()
                .filter(t -> !isRepositoryDisabled(t))
                .map(t -> submitProcess(event, t, initiatorResolver, cfgEnricher, exclusiveResolver))
                .collect(Collectors.toList()) // collect all "futures"
                .stream()
                .map(TriggerProcessExecutor::resolve)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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

        Subject s = PrincipalUtils.getSubject();

        requiredRoles.forEach((k, v) -> {
            if (eventName.matches(k) && !s.hasRole(v)) {
                throw new ConcordApplicationException("'" + v + "' role is required", Response.Status.FORBIDDEN);
            }
        });
    }

    private boolean isRepositoryDisabled(TriggerEntry t) {
        return repositoryDao.get(t.getRepositoryId()).isDisabled();
    }

    private Future<PartialProcessKey> submitProcess(Event event,
                                                    TriggerEntry t,
                                                    TriggerEventInitiatorResolver initiatorResolver,
                                                    ProcessConfigurationEnricher cfgEnricher,
                                                    TriggerExclusiveParamsResolver exclusiveResolver) {

        UserEntry initiator;
        try {
            initiator = initiatorResolver.resolve(t, event);
        } catch (Exception e) {
            log.error("process ['{}', '{}', '{}'] -> error", event.id(), event.name(), t.getId(), e);
            SettableFuture<PartialProcessKey> f = SettableFuture.create();
            f.set(null);
            return f;
        }

        return executor.submit(() -> {
            Map<String, Object> args = new HashMap<>();
            if (t.getArguments() != null) {
                args.putAll(t.getArguments());
            }

            Map<String, Object> eventAttributes = new LinkedHashMap<>(event.attributes());
            eventAttributes.put("id", event.id());

            args.put("event", ExpressionUtils.escapeMap(eventAttributes));

            Map<String, Object> cfg = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);

            if (TriggerUtils.getEntryPoint(t) != null) {
                cfg.put(Constants.Request.ENTRY_POINT_KEY, TriggerUtils.getEntryPoint(t));
            }

            if (t.getActiveProfiles() != null) {
                cfg.put(Constants.Request.ACTIVE_PROFILES_KEY, t.getActiveProfiles());
            }

            Map<String, Object> exclusive = exclusiveResolver.resolve(t);
            if (exclusive != null && !exclusive.isEmpty()) {
                // avoid saving empty objects into the cfg
                cfg.put(Constants.Request.EXCLUSIVE, exclusive);
            }

            if (cfgEnricher != null) {
                cfg = cfgEnricher.enrich(t, cfg);
            }

            try {
                UUID orgId = projectDao.getOrgId(t.getProjectId());

                PartialProcessKey pk = startProcess(event.id(), orgId, t, cfg, initiator);
                log.info("process ['{}'] -> new process ('{}') triggered by {}", event.id(), pk, t);
                return pk;
            } catch (Exception e) {
                log.error("process ['{}', '{}', '{}'] -> error", event.id(), event.name(), t.getId(), e);
                return null;
            }
        });
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
}
