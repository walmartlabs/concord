package com.walmartlabs.concord.server.process.event;

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
import com.walmartlabs.concord.server.process.ProcessEventEntry;
import com.walmartlabs.concord.server.process.ProcessEventType;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static org.jooq.impl.DSL.*;

@Named
public class EventDao extends AbstractDao {

    @Inject
    public EventDao(Configuration cfg) {
        super(cfg);
    }

    public List<ProcessEventEntry> list(UUID instanceId, Timestamp geTimestamp, int limit) {
        try (DSLContext tx = DSL.using(cfg)) {

            SelectConditionStep<Record4<UUID, String, Timestamp, String>> q = tx
                    .select(PROCESS_EVENTS.EVENT_ID,
                            PROCESS_EVENTS.EVENT_TYPE,
                            PROCESS_EVENTS.EVENT_DATE,
                            PROCESS_EVENTS.EVENT_DATA.cast(String.class))
                    .from(PROCESS_EVENTS)
                    .where(PROCESS_EVENTS.INSTANCE_ID.eq(instanceId));

            if (geTimestamp != null) {
                q.and(PROCESS_EVENTS.EVENT_DATE.ge(geTimestamp));
            }

            if (limit > 0) {
                q.limit(limit);
            }

            return q.orderBy(PROCESS_EVENTS.EVENT_DATE)
                    .fetch(EventDao::toEntry);
        }
    }

    public void insert(UUID instanceId, ProcessEventType eventType, String eventData) {
        tx(tx -> insert(tx, instanceId, eventType, eventData));
    }

    public void insert(DSLContext tx, UUID instanceId, ProcessEventType eventType, String eventData) {
        tx.insertInto(PROCESS_EVENTS)
                .columns(PROCESS_EVENTS.INSTANCE_ID,
                        PROCESS_EVENTS.EVENT_TYPE,
                        PROCESS_EVENTS.EVENT_DATE,
                        PROCESS_EVENTS.EVENT_DATA)
                .values(value(instanceId),
                        value(eventType.name()),
                        currentTimestamp(),
                        field("?::jsonb", eventData))
                .execute();
    }

    private static ProcessEventEntry toEntry(Record4<UUID, String, Timestamp, String> r) {
        return new ProcessEventEntry(r.value1(), ProcessEventType.valueOf(r.value2()), r.value3(), r.value4());
    }
}
