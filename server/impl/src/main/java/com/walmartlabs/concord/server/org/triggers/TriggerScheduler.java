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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.task.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.bind.DatatypeConverter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Named("trigger-scheduler")
@Singleton
public class TriggerScheduler implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(TriggerScheduler.class);

    private static final UUID INITIATOR_ID = UUID.fromString("1f9ae527-e7ab-42c0-b0e5-0092f9285f22");
    private static final String INITIATOR = "cron";

    private static final String REALM = "cron";
    private static final String EVENT_SOURCE = "cron";

    private final Date startedAt;
    private final TriggerScheduleDao scheduleDao;
    private final ProcessManager processManager;
    private final ProcessSecurityContext processSecurityContext;
    private final TriggersConfiguration triggerCfg;

    @Inject
    public TriggerScheduler(TriggerScheduleDao scheduleDao,
                            ProcessManager processManager,
                            ProcessSecurityContext processSecurityContext,
                            TriggersConfiguration triggerCfg) {

        this.startedAt = new Date();
        this.scheduleDao = scheduleDao;
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

            if (e.getFireAt().after(startedAt)) {
                startProcess(e);
            }
        }
    }

    private void startProcess(TriggerSchedulerEntry t) {
        if (isDisabled(EVENT_SOURCE)) {
            log.warn("startProcess ['{}'] -> disabled, skipping", t);
            return;
        }

        log.info("run -> starting {}...", t);

        Map<String, Object> args = new HashMap<>();
        if (t.getArguments() != null) {
            args.putAll(t.getArguments());
        }
        args.put("event", makeEvent(t));

        startProcess(t.getTriggerId(), t.getOrgId(), t.getProjectId(), t.getRepoId(), t.getEntryPoint(), args);
    }

    private void startProcess(UUID triggerId, UUID orgId, UUID projectId, UUID repoId, String flowName, Map<String, Object> args) {
        PartialProcessKey processKey = PartialProcessKey.create();

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.Request.ARGUMENTS_KEY, args);

        Payload payload;
        try {
            payload = PayloadBuilder.start(processKey)
                    .initiator(INITIATOR_ID, INITIATOR)
                    .organization(orgId)
                    .project(projectId)
                    .repository(repoId)
                    .entryPoint(flowName)
                    .configuration(request)
                    .build();
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error creating a payload",
                    triggerId, orgId, projectId, repoId, flowName, e);
            return;
        }

        try {
            processSecurityContext.runAs(REALM, INITIATOR, () -> processManager.start(payload, false));
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error starting process",
                    triggerId, orgId, projectId, repoId, flowName, e);
        }

        log.info("startProcess ['{}', '{}', '{}', '{}', '{}'] -> process '{}' started", triggerId, orgId, projectId, repoId, flowName, processKey);
    }

    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isDisabled(String eventName) {
        return triggerCfg.isDisableAll() || triggerCfg.getDisabled().contains(eventName);
    }

    private static Map<String, Object> makeEvent(TriggerSchedulerEntry t) {
        Map<String, Object> m = new HashMap<>();
        m.put(Constants.Trigger.CRON_SPEC, t.getCronSpec());
        m.put(Constants.Trigger.CRON_TIMEZONE, t.getTimezone());

        Calendar c = Calendar.getInstance();
        c.setTime(t.getFireAt());
        m.put(Constants.Trigger.CRON_EVENT_FIREAT, DatatypeConverter.printDateTime(c));

        return m;
    }
}
