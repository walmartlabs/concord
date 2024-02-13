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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.Repositories;
import com.walmartlabs.concord.server.jooq.tables.Triggers;
import org.jooq.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.walmartlabs.concord.db.PgUtils.jsonbStripNulls;
import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.TriggerSchedule.TRIGGER_SCHEDULE;
import static com.walmartlabs.concord.server.jooq.tables.Triggers.TRIGGERS;
import static org.jooq.impl.DSL.*;

public class TriggerScheduleDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public TriggerScheduleDao(@MainDB Configuration cfg,
                              ConcordObjectMapper objectMapper) {

        super(cfg);
        this.objectMapper = objectMapper;
    }

    public TriggerSchedulerEntry findNext() {
        return txResult(tx -> {
            // TODO fetch everything in a single request?
            Map<String, Object> e = tx.select(TRIGGER_SCHEDULE.TRIGGER_ID, TRIGGER_SCHEDULE.FIRE_AT)
                    .from(TRIGGER_SCHEDULE)
                    .where(TRIGGER_SCHEDULE.FIRE_AT.le(currentOffsetDateTime()))
                    .limit(1)
                    .forUpdate()
                    .skipLocked()
                    .fetchOneMap();

            if (e == null) {
                return null;
            }

            UUID id = (UUID) e.get(TRIGGER_SCHEDULE.TRIGGER_ID.getName());
            OffsetDateTime fireAt = (OffsetDateTime) e.get(TRIGGER_SCHEDULE.FIRE_AT.getName());

            Triggers t = TRIGGERS.as("t");
            Projects p = PROJECTS.as("p");
            Repositories r = REPOSITORIES.as("r");
            Organizations o = ORGANIZATIONS.as("o");

            Field<UUID> orgIdField = select(p.ORG_ID).from(p).where(p.PROJECT_ID.eq(t.PROJECT_ID)).asField();

            Record13<UUID, UUID, String, UUID, String, UUID, String, String[], JSONB, JSONB, JSONB, OffsetDateTime, String> record = tx.select(
                    t.TRIGGER_ID,
                    orgIdField,
                    o.ORG_NAME,
                    t.PROJECT_ID,
                    p.PROJECT_NAME,
                    t.REPO_ID,
                    r.REPO_NAME,
                    t.ACTIVE_PROFILES,
                    jsonbStripNulls(t.ARGUMENTS),
                    jsonbStripNulls(t.TRIGGER_CFG),
                    jsonbStripNulls(t.CONDITIONS),
                    currentOffsetDateTime(),
                    t.EVENT_SOURCE)
                    .from(t, p, r, o)
                    .where(t.TRIGGER_ID.eq(id).
                            and(t.PROJECT_ID.eq(p.PROJECT_ID)).
                            and(p.PROJECT_ID.eq(r.PROJECT_ID)).
                            and(p.ORG_ID.eq(o.ORG_ID)).
                            and(t.REPO_ID.eq(r.REPO_ID)))
                    .fetchOne();

            if (record == null) {
                return null;
            }

            OffsetDateTime now = record.value12();
            Map<String, Object> conditions = objectMapper.fromJSONB(record.value11());

            ZoneId zoneId = null;
            if (conditions.get(Constants.Trigger.CRON_TIMEZONE) != null) {
                zoneId = TimeZone.getTimeZone((String) conditions.get(Constants.Trigger.CRON_TIMEZONE)).toZoneId();
            }

            OffsetDateTime nextExecutionAt = CronUtils.nextExecution(now, (String) conditions.get(Constants.Trigger.CRON_SPEC), zoneId);
            updateFireAt(tx, id, nextExecutionAt);

            Map<String, Object> arguments = objectMapper.fromJSONB(record.value9());
            Map<String, Object> cfg = objectMapper.fromJSONB(record.value10());

            TriggerEntry triggerEntry = new TriggerEntryBuilder()
                    .id(record.value1())
                    .orgId(record.value2())
                    .orgName(record.value3())
                    .projectId(record.value4())
                    .projectName(record.value5())
                    .repositoryId(record.value6())
                    .repositoryName(record.value7())
                    .eventSource(record.value13())
                    .activeProfiles(toList(record.value8()))
                    .arguments(arguments != null ? arguments : Collections.emptyMap())
                    .conditions(conditions)
                    .cfg(cfg != null ? cfg : Collections.emptyMap())
                    .build();

            return TriggerSchedulerEntry.builder()
                    .fireAt(fireAt)
                    .nextExecutionAt(nextExecutionAt)
                    .trigger(triggerEntry)
                    .build();
        });
    }

    public OffsetDateTime now() {
        return txResult(tx -> tx.select(currentOffsetDateTime().as("now"))
                .fetchOne(field("now", OffsetDateTime.class)));
    }

    public void insert(DSLContext tx, UUID triggerId, OffsetDateTime fireAt) {
        tx.insertInto(TRIGGER_SCHEDULE)
                .columns(TRIGGER_SCHEDULE.TRIGGER_ID, TRIGGER_SCHEDULE.FIRE_AT)
                .values(triggerId, fireAt)
                .execute();
    }

    public void remove(UUID triggerId) {
        tx(tx -> tx.deleteFrom(TRIGGER_SCHEDULE)
                .where(TRIGGER_SCHEDULE.TRIGGER_ID.eq(triggerId))
                .execute());
    }

    private static void updateFireAt(DSLContext tx, UUID triggerId, OffsetDateTime fireAt) {
        tx.update(TRIGGER_SCHEDULE)
                .set(TRIGGER_SCHEDULE.FIRE_AT, fireAt)
                .where(TRIGGER_SCHEDULE.TRIGGER_ID.eq(triggerId))
                .execute();
    }

    private static <E> List<E> toList(E[] arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(arr);
    }
}
