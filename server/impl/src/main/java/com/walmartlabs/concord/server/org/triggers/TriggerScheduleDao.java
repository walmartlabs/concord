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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.jooq.tables.Triggers;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record10;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.TriggerSchedule.TRIGGER_SCHEDULE;
import static com.walmartlabs.concord.server.jooq.tables.Triggers.TRIGGERS;
import static org.jooq.impl.DSL.*;

@Named
public class TriggerScheduleDao extends AbstractDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public TriggerScheduleDao(@Named("app") Configuration cfg) {
        super(cfg);
    }

    public TriggerSchedulerEntry findNext() {
        return txResult(tx -> {
            Map<String, Object> e = tx.select(TRIGGER_SCHEDULE.TRIGGER_ID, TRIGGER_SCHEDULE.FIRE_AT)
                    .from(TRIGGER_SCHEDULE)
                    .where(TRIGGER_SCHEDULE.FIRE_AT.le(currentTimestamp()))
                    .limit(1)
                    .forUpdate()
                    .skipLocked()
                    .fetchOneMap();

            if (e == null) {
                return null;
            }

            UUID id = (UUID) e.get(TRIGGER_SCHEDULE.TRIGGER_ID.getName());
            Date fireAt = (Date) e.get(TRIGGER_SCHEDULE.FIRE_AT.getName());

            Triggers t = TRIGGERS.as("t");

            Field<UUID> orgIdField = select(PROJECTS.ORG_ID).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(t.PROJECT_ID)).asField();
            Field<String> specField = field("{0}->>'spec'", String.class, t.CONDITIONS);
            Field<String> timezoneField = field("{0}->>'timezone'", String.class, t.CONDITIONS);

            Record10<UUID, UUID, UUID, UUID, String, String, String[], String, String, Timestamp> r = tx.select(
                    t.TRIGGER_ID,
                    orgIdField,
                    t.PROJECT_ID,
                    t.REPO_ID,
                    specField,
                    timezoneField,
                    t.ACTIVE_PROFILES,
                    t.ARGUMENTS.cast(String.class),
                    t.TRIGGER_CFG.cast(String.class),
                    currentTimestamp())
                    .from(t)
                    .where(t.TRIGGER_ID.eq(id))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            TriggerSchedulerEntry result = new TriggerSchedulerEntry(
                    fireAt,
                    r.value1(),
                    r.value2(),
                    r.value3(),
                    r.value4(),
                    r.value5(),
                    r.value6(),
                    toList(r.value7()),
                    deserialize(r.value8()),
                    deserialize(r.value9()));

            ZoneId zoneId = null;
            if (result.getTimezone() != null) {
                zoneId = TimeZone.getTimeZone(result.getTimezone()).toZoneId();
            }
            Instant now = r.value10().toInstant();
            updateFireAt(tx, id, CronUtils.nextExecution(now, result.getCronSpec(), zoneId));

            return result;
        });
    }

    public Instant now() {
        return txResult(tx -> tx.select(currentTimestamp().as("now"))
                .fetchOne(field("now", Timestamp.class))
                .toInstant());
    }

    public void insert(DSLContext tx, UUID triggerId, Instant fireAt) {
        tx.insertInto(TRIGGER_SCHEDULE)
                .columns(TRIGGER_SCHEDULE.TRIGGER_ID, TRIGGER_SCHEDULE.FIRE_AT)
                .values(value(triggerId), value(Timestamp.from(fireAt)))
                .execute();
    }

    private void updateFireAt(DSLContext tx, UUID triggerId, Instant fireAt) {
        tx.update(TRIGGER_SCHEDULE)
                .set(TRIGGER_SCHEDULE.FIRE_AT, Timestamp.from(fireAt))
                .where(TRIGGER_SCHEDULE.TRIGGER_ID.eq(triggerId))
                .execute();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deserialize(String ab) {
        if (ab == null) {
            return null;
        }

        try {
            return objectMapper.readValue(ab, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <E> List<E> toList(E[] arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(arr);
    }
}
