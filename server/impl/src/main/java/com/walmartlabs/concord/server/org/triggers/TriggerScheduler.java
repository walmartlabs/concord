package com.walmartlabs.concord.server.org.triggers;

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

import com.walmartlabs.concord.common.DateTimeUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Named("trigger-scheduler")
@Singleton
public class TriggerScheduler implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(TriggerScheduler.class);

    private static final UUID INITIATOR_ID = UUID.fromString("1f9ae527-e7ab-42c0-b0e5-0092f9285f22");
    private static final String INITIATOR = "cron";

    private static final String EVENT_SOURCE = "cron";

    private final OffsetDateTime startedAt;
    private final TriggerScheduleDao scheduleDao;
    private final RepositoryDao repositoryDao;
    private final ProcessManager processManager;
    private final ProcessSecurityContext processSecurityContext;
    private final TriggersConfiguration triggerCfg;

    @Inject
    public TriggerScheduler(TriggerScheduleDao scheduleDao,
                            RepositoryDao repositoryDao,
                            ProcessManager processManager,
                            ProcessSecurityContext processSecurityContext,
                            TriggersConfiguration triggerCfg) {

        this.startedAt = OffsetDateTime.now();
        this.scheduleDao = scheduleDao;
        this.repositoryDao = repositoryDao;
        this.processManager = processManager;
        this.processSecurityContext = processSecurityContext;
        this.triggerCfg = triggerCfg;
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
            if (e.getFireAt().isAfter(startedAt)) {
                startProcess(e);
            }
        }
    }

    private void startProcess(TriggerSchedulerEntry t) {
        if (isDisabled(EVENT_SOURCE)) {
            log.warn("startProcess ['{}'] -> disabled, skipping", t);
            return;
        }

        if (isRepositoryDisabled(t)) {
            log.warn("startProcess ['{}'] -> repository is disabled, skipping", t);
            return;
        }

        log.info("run -> starting {}...", t);

        Map<String, Object> args = new HashMap<>();
        if (t.getArguments() != null) {
            args.putAll(t.getArguments());
        }
        args.put("event", makeEvent(t));
        log.info("Event: {}", args.get("event"));

        Map<String, Object> cfg = t.getCfg();
        cfg.put(Constants.Request.ARGUMENTS_KEY, args);

        PartialProcessKey processKey = PartialProcessKey.create();
        UUID triggerId = t.getTriggerId();
        UUID orgId = t.getOrgId();
        UUID projectId = t.getProjectId();
        UUID repoId = t.getRepositoryId();
        String entryPoint = t.getEntryPoint();
        Collection<String> activeProfiles = t.getActiveProfiles();

        Payload payload;
        try {
            payload = PayloadBuilder.start(processKey)
                    .initiator(INITIATOR_ID, INITIATOR)
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
            return;
        }

        try {
            processSecurityContext.runAs(INITIATOR_ID, () -> processManager.start(payload));
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error starting process",
                    triggerId, orgId, projectId, repoId, entryPoint, e);
            return;
        }

        log.info("startProcess ['{}', '{}', '{}', '{}', '{}'] -> process '{}' started",
                triggerId, orgId, projectId, repoId, entryPoint, processKey);
    }

    private boolean isRepositoryDisabled(TriggerSchedulerEntry t) {
        return repositoryDao.get(t.getRepositoryId()).isDisabled();
    }

    private boolean isDisabled(String eventName) {
        return triggerCfg.isDisableAll() || triggerCfg.getDisabled().contains(eventName);
    }

    private static Map<String, Object> makeEvent(TriggerSchedulerEntry t) {
        Map<String, Object> m = new HashMap<>();
        m.put(Constants.Trigger.CRON_SPEC, t.getConditions().get(Constants.Trigger.CRON_SPEC));
        m.put(Constants.Trigger.CRON_TIMEZONE, t.getConditions().get(Constants.Trigger.CRON_TIMEZONE));
        m.put(Constants.Trigger.CRON_EVENT_FIREAT, DateTimeUtils.toIsoString(t.getFireAt()));
        return m;
    }
}
