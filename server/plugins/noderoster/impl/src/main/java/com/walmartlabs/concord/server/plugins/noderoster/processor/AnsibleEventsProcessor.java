package com.walmartlabs.concord.server.plugins.noderoster.processor;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.plugins.noderoster.cfg.AnsibleEventsConfiguration;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.plugins.noderoster.processor.EventMarkerDao.EventMarker;
import static org.jooq.impl.DSL.function;

@Named("noderoster/ansible-events-processor")
public class AnsibleEventsProcessor extends AbstractEventProcessor<AnsibleEvent> {

    private static final String NAME = "noderoster/ansible-events-processor";

    private final EventsDao eventsDao;
    private final List<Processor> processors;

    private final long interval;

    @Inject
    public AnsibleEventsProcessor(AnsibleEventsConfiguration cfg,
                                  EventMarkerDao eventMarkerDao,
                                  EventsDao eventsDao,
                                  Map<String, Processor> processors) {

        super(NAME, eventMarkerDao, cfg.getFetchLimit());

        this.eventsDao = eventsDao;
        this.processors = new ArrayList<>(processors.values());
        this.interval = cfg.getPeriod();
    }

    @Override
    public long getIntervalInSec() {
        return interval;
    }

    @Override
    protected List<AnsibleEvent> processEvents(DSLContext tx, EventMarker marker, int fetchLimit) {
        List<AnsibleEvent> events = eventsDao.list(marker, fetchLimit);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        for (Processor p : processors) {
            p.process(events);
        }
        return events;
    }

    @Named
    public static class EventsDao extends AbstractDao {

        private final ObjectMapper objectMapper;

        @Inject
        public EventsDao(@MainDB Configuration cfg) {
            super(cfg);

            this.objectMapper = new ObjectMapper();
        }

        public List<AnsibleEvent> list(EventMarker marker, int count) {
            return txResult(tx -> list(tx, marker, count));
        }

        private List<AnsibleEvent> list(DSLContext tx, EventMarker marker, int count) {
            ProcessQueue pq = PROCESS_QUEUE.as("pq");
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            Field<String> username = tx.select(USERS.USERNAME).from(USERS).where(USERS.USER_ID.eq(pq.INITIATOR_ID)).asField();

            Field<Object> eventData = function("jsonb_strip_nulls", Object.class, pe.EVENT_DATA);
            SelectConditionStep<Record9<UUID, Long, UUID, Timestamp, Timestamp, Object, String, UUID, UUID>> s = tx.select(
                    pe.EVENT_ID,
                    pe.EVENT_SEQ,
                    pe.INSTANCE_ID,
                    pe.INSTANCE_CREATED_AT,
                    pe.EVENT_DATE,
                    eventData,
                    username,
                    pq.INITIATOR_ID,
                    pq.PROJECT_ID)
                    .from(pe)
                    .innerJoin(pq).on(pq.INSTANCE_ID.eq(pe.INSTANCE_ID).and(pq.CREATED_AT.eq(pe.INSTANCE_CREATED_AT)))
                    .where(pe.EVENT_TYPE.eq("ANSIBLE")
                            .and(pe.EVENT_SEQ.greaterThan(marker.eventSeq())));

            return s.orderBy(pe.EVENT_SEQ)
                    .limit(count)
                    .fetch(this::toEntity);
        }

        private AnsibleEvent toEntity(Record9<UUID, Long, UUID, Timestamp, Timestamp, Object, String, UUID, UUID> r) {
            return AnsibleEvent.builder()
                    .id(r.value1())
                    .eventSeq(r.value2())
                    .instanceId(r.value3())
                    .instanceCreatedAt(r.value4())
                    .eventDate(r.value5())
                    .data(new EventData(deserialize(r.value6())))
                    .initiator(r.value7())
                    .initiatorId(r.value8())
                    .projectId(r.value9())
                    .build();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> deserialize(Object ab) {
            try {
                return objectMapper.readValue(String.valueOf(ab), Map.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
