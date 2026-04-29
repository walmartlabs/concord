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

import com.walmartlabs.concord.runtime.model.Trigger;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public class CronTriggerProcessor {

    private final TriggerScheduleDao schedulerDao;

    @Inject
    public CronTriggerProcessor(TriggerScheduleDao schedulerDao) {
        this.schedulerDao = schedulerDao;
    }

    public void process(DSLContext tx, UUID triggerId, Trigger t) {
        Map<String, Object> conditions = t.conditions();
        if (conditions == null) {
            throw new ValidationErrorsException("cron trigger '" + triggerId + "' without params");
        }

        String spec = (String) conditions.get(Constants.Trigger.CRON_SPEC);

        if (spec == null) {
            throw new ValidationErrorsException("cron trigger '" + triggerId + "' without spec");
        }

        ZoneId zoneId = null;
        String timezone = (String) conditions.get(Constants.Trigger.CRON_TIMEZONE);
        if (timezone != null) {
            if (!validTimeZone(timezone)) {
                throw new ValidationErrorsException("cron trigger '" + triggerId + "' invalid timezone '" + timezone + "'");
            }

            zoneId = TimeZone.getTimeZone(timezone).toZoneId();
        }

        OffsetDateTime fireAt = CronUtils.nextExecution(schedulerDao.now(), spec, zoneId);
        if (fireAt == null) {
            throw new ValidationErrorsException("cron trigger '" + triggerId + "' spec is empty");
        }

        schedulerDao.insert(tx, triggerId, fireAt);
    }

    private static boolean validTimeZone(String timezone) {
        return Arrays.asList(TimeZone.getAvailableIDs()).contains(timezone);
    }
}
