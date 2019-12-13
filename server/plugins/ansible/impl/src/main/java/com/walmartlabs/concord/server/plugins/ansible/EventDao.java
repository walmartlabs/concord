package com.walmartlabs.concord.server.plugins.ansible;

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
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;

@Named
public class EventDao extends AbstractDao {

    @Inject
    public EventDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public List<ProcessEventEntry> list(ProcessKey processKey, Map<String, String> dataFilter) {
        try (DSLContext tx = DSL.using(cfg)) {

            SelectConditionStep<Record4<UUID, String, Timestamp, String>> q = tx
                    .select(PROCESS_EVENTS.EVENT_ID,
                            PROCESS_EVENTS.EVENT_TYPE,
                            PROCESS_EVENTS.EVENT_DATE,
                            PROCESS_EVENTS.EVENT_DATA.cast(String.class))
                    .from(PROCESS_EVENTS)
                    .where(PROCESS_EVENTS.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(PROCESS_EVENTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())));

            q.and(PROCESS_EVENTS.EVENT_TYPE.eq(Constants.ANSIBLE_EVENT_TYPE));

            if (dataFilter != null) {
                dataFilter.forEach((k, v) -> {
                    q.and(field("{0}->>{1}", Object.class, PROCESS_EVENTS.EVENT_DATA, inline(k)).eq(v));
                });
            }

            return q.orderBy(PROCESS_EVENTS.EVENT_DATE)
                    .fetch(EventDao::toEntry);
        }
    }

    private static ProcessEventEntry toEntry(Record4<UUID, String, Timestamp, String> r) {
        return ProcessEventEntry.builder()
                .id(r.value1())
                .eventType(r.value2())
                .eventDate(r.value3())
                .data(r.value4())
                .build();
    }
}
