package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.common.DateTimeUtils;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.CronTriggerRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.audit.ActionSource;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TriggerScheduler implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(TriggerScheduler.class);

    private static final String DEFAULT_POLICY_MESSAGE = "process start interval can''t be less then {2}";

    private static final Initiator CRON = Initiator.of(UUID.fromString("1f9ae527-e7ab-42c0-b0e5-0092f9285f22"), "cron");

    private static final String EVENT_SOURCE = "cron";

    private final OffsetDateTime startedAt;
    private final TriggerScheduleDao scheduleDao;
    private final RepositoryDao repositoryDao;
    private final ProcessManager processManager;
    private final UserSecurityContext userSecurityContext;
    private final TriggersConfiguration triggerCfg;
    private final SecretManager secretManager;
    private final UserManager userManager;
    private final AuditLog auditLog;
    private final PolicyManager policyManager;

    @Inject
    public TriggerScheduler(TriggerScheduleDao scheduleDao,
                            RepositoryDao repositoryDao,
                            ProcessManager processManager,
                            UserSecurityContext userSecurityContext,
                            TriggersConfiguration triggerCfg,
                            SecretManager secretManager,
                            UserManager userManager,
                            AuditLog auditLog,
                            PolicyManager policyManager) {
        this.secretManager = secretManager;
        this.userManager = userManager;
        this.auditLog = auditLog;
        this.policyManager = policyManager;

        this.startedAt = OffsetDateTime.now();
        this.scheduleDao = scheduleDao;
        this.repositoryDao = repositoryDao;
        this.processManager = processManager;
        this.userSecurityContext = userSecurityContext;
        this.triggerCfg = triggerCfg;
    }

    @Override
    public String getId() {
        return "trigger-scheduler";
    }

    @Override
    public long getIntervalInSec() {
        return TimeUnit.MINUTES.toSeconds(1);
    }

    @Override
    public void performTask() {
        while (!Thread.currentThread().isInterrupted()) {
            TriggerSchedulerEntry e = scheduleDao.findNext();
            if (e == null) {
                break;
            }
            if (e.fireAt().isAfter(startedAt)) {
                startProcess(e);
            }
        }
    }

    private void startProcess(TriggerSchedulerEntry triggerSchedulerEntry) {
        if (isDisabled(EVENT_SOURCE)) {
            log.warn("startProcess ['{}'] -> disabled, skipping", triggerSchedulerEntry);
            return;
        }

        TriggerEntry t = triggerSchedulerEntry.trigger();
        if (isRepositoryDisabled(t.getRepositoryId())) {
            log.warn("startProcess ['{}'] -> repository is disabled, skipping", triggerSchedulerEntry);
            scheduleDao.remove(t.getId());
            return;
        }

        List<CheckResult.Item<CronTriggerRule, Duration>> deny = checkPolicy(triggerSchedulerEntry);
        if (!deny.isEmpty()) {
            log.warn("startProcess ['{}'] -> policy violated", triggerSchedulerEntry);
            logFailedToStart(t, buildErrorMessage(deny));
            scheduleDao.remove(t.getId());
            return;
        }

        log.info("run -> starting {}...", t);

        Map<String, Object> args = new HashMap<>();
        if (t.getArguments() != null) {
            args.putAll(t.getArguments());
        }
        args.put("event", makeEvent(triggerSchedulerEntry.fireAt(), t));

        Map<String, Object> cfg = new HashMap<>(t.getCfg());
        cfg.put(Constants.Request.ARGUMENTS_KEY, args);

        PartialProcessKey processKey = PartialProcessKey.create();
        UUID triggerId = t.getId();
        UUID orgId = t.getOrgId();
        UUID projectId = t.getProjectId();
        UUID repoId = t.getRepositoryId();
        String entryPoint = TriggerUtils.getEntryPoint(t);
        Collection<String> activeProfiles = t.getActiveProfiles();

        Initiator initiator;
        try {
            initiator = getInitiator(t);
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}', '{}', {}] -> error getting initiator: {}",
                    triggerId, orgId, projectId, repoId, entryPoint, activeProfiles, e.getMessage());
            logFailedToStart(t, e.getMessage());
            return;
        }

        Payload payload;
        try {
            payload = PayloadBuilder.start(processKey)
                    .initiator(initiator.id(), initiator.name())
                    .organization(orgId)
                    .project(projectId)
                    .repository(repoId)
                    .entryPoint(entryPoint)
                    .activeProfiles(activeProfiles)
                    .triggeredBy(TriggeredByEntry.builder().trigger(t).build())
                    .configuration(cfg)
                    .build();
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}', '{}', {}] -> error creating a payload",
                    triggerId, orgId, projectId, repoId, entryPoint, activeProfiles, e);
            logFailedToStart(t, e.getMessage());
            return;
        }

        try {
            userSecurityContext.runAs(initiator.id(), () -> processManager.start(payload));
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error starting process",
                    triggerId, orgId, projectId, repoId, entryPoint, e);
            logFailedToStart(t, e.getMessage());
            return;
        }

        log.info("startProcess ['{}', '{}', '{}', '{}', '{}'] -> process '{}' started",
                triggerId, orgId, projectId, repoId, entryPoint, processKey);
    }

    private List<CheckResult.Item<CronTriggerRule, Duration>> checkPolicy(TriggerSchedulerEntry entry) {
        PolicyEngine policy = policyManager.get(entry.trigger().getOrgId(), entry.trigger().getProjectId(), null);
        if (policy == null) {
            return Collections.emptyList();
        }

        CheckResult<CronTriggerRule, Duration> result = policy.getCronTriggerPolicy().check(entry.fireAt(), entry.nextExecutionAt());
        return result.getDeny();
    }

    private void logFailedToStart(TriggerEntry t, String msg) {
        try {
            userSecurityContext.runAs(CRON.id(), () -> {
                AuditLog.withActionSource(ActionSource.SYSTEM, Collections.emptyMap(), () -> auditLog.add(AuditObject.PROCESS, AuditAction.CREATE)
                        .field("orgId", t.getOrgId())
                        .field("projectId", t.getProjectId())
                        .field("status", "FAILED")
                        .field("reason", msg)
                        .log());
                return null;
            });
        } catch (Exception e) {
            log.error("logFailedToStart ['{}'] -> error", t.getId(), e);
        }
    }

    private Initiator getInitiator(TriggerEntry t) throws Exception {
        TriggerRunAs runAs = getRunAs(t);
        if (runAs == null) {
            return CRON;
        }

        ApiKeyEntry apiKey = userSecurityContext.runAs(CRON.id(), () -> secretManager.assertApiKey(SecretManager.AccessScope.project(t.getProjectId()), t.getOrgId(), runAs.secretName(), null));
        UserEntry u = userManager.get(apiKey.getUserId()).orElse(null);
        if (u == null) {
            throw new RuntimeException("Can't find user with API token from secret '" + runAs.secretName() + "'");
        }
        if (u.isDisabled()) {
            throw new RuntimeException("User '" + u.getName() + "' (" + u.getId() + ") disabled");
        }
        return Initiator.of(u.getId(), u.getName());
    }

    private boolean isRepositoryDisabled(UUID repositoryId) {
        return repositoryDao.get(repositoryId).isDisabled();
    }

    private boolean isDisabled(String eventName) {
        return triggerCfg.isDisableAll() || triggerCfg.getDisabled().contains(eventName);
    }

    private static TriggerRunAs getRunAs(TriggerEntry t) {
        return TriggerRunAs.from(MapUtils.getMap(t.getCfg(), "runAs", Collections.emptyMap()));
    }

    private static Map<String, Object> makeEvent(OffsetDateTime fireAt, TriggerEntry t) {
        Map<String, Object> m = new HashMap<>();
        m.put(Constants.Trigger.CRON_SPEC, t.getConditions().get(Constants.Trigger.CRON_SPEC));
        m.put(Constants.Trigger.CRON_TIMEZONE, t.getConditions().get(Constants.Trigger.CRON_TIMEZONE));
        m.put(Constants.Trigger.CRON_EVENT_FIREAT, DateTimeUtils.toIsoString(fireAt));
        return m;
    }


    private static String buildErrorMessage(List<CheckResult.Item<CronTriggerRule, Duration>> errors) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<CronTriggerRule, Duration> e : errors) {
            CronTriggerRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : DEFAULT_POLICY_MESSAGE;
            Duration actual = e.getEntity();
            long min = r.minInterval();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actual, min, Duration.ofSeconds(min))).append(';');
        }
        return sb.toString();
    }

    @Value.Immutable
    interface Initiator {

        @Value.Parameter
        UUID id();

        @Value.Parameter
        String name();

        static Initiator of(UUID id, String name) {
            return ImmutableInitiator.of(id, name);
        }
    }
}
