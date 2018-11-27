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

import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.sdk.Constants;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.UUID;

@Named("cron")
public class CronTriggerProcessor implements TriggerProcessor {

    private static final Logger log = LoggerFactory.getLogger(CronTriggerProcessor.class);

    private final TriggerScheduleDao schedulerDao;

    @Inject
    public CronTriggerProcessor(TriggerScheduleDao schedulerDao) {
        this.schedulerDao = schedulerDao;
    }

    @Override
    public void process(DSLContext tx, UUID triggerId, Trigger t) {
        if (t.getParams() == null) {
            log.warn("process ['{}'] -> cron trigger without params, ignore", triggerId);
            return;
        }

        String spec = (String) t.getParams().get(Constants.Trigger.CRON_SPEC);

        if (spec == null) {
            log.warn("process ['{}'] -> cron trigger without spec, ignore", triggerId);
            return;
        }

        ZoneId zoneId = null;
        String timezone = (String) t.getParams().get(Constants.Trigger.CRON_TIMEZONE);
        if (timezone != null) {
            if (!validTimeZone(timezone)) {
                log.warn("process ['{}'] -> cron trigger invalid timezone, ignore", triggerId);
                return;
            }

            zoneId = TimeZone.getTimeZone(timezone).toZoneId();
        }

        Instant fireAt = CronUtils.nextExecution(schedulerDao.now(), spec, zoneId);
        if (fireAt == null) {
            log.warn("process ['{}'] -> cron spec empty", triggerId);
            return;
        }

        schedulerDao.insert(tx, triggerId, fireAt);
    }

    private static boolean validTimeZone(String timezone) {
        return Arrays.asList(TimeZone.getAvailableIDs()).contains(timezone);
    }
}
