package com.walmartlabs.concord.server.plugins.ansible;

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
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static org.jooq.impl.DSL.*;

public class EventFetcher extends AbstractEventProcessor<EventProcessor.Event> {

    private static final String PROCESSOR_NAME = "ansible-event-processor";

    private final AnsibleEventsConfiguration cfg;
    private final AnsibleEventDao dao;
    private final List<EventProcessor> processors;

    @Inject
    public EventFetcher(AnsibleEventsConfiguration cfg,
                        EventMarkerDao eventMarkerDao,
                        AnsibleEventDao dao,
                        List<EventProcessor> processors) {
        super(PROCESSOR_NAME, eventMarkerDao, cfg.getFetchLimit());
        this.cfg = cfg;
        this.dao = dao;
        this.processors = processors;
    }

    @Override
    public String getId() {
        return "ansible-event-processor";
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod().getSeconds();
    }

    @Override
    protected List<EventProcessor.Event> processEvents(DSLContext tx, EventMarkerDao.EventMarker marker, int fetchLimit) {
        List<EventProcessor.Event> events = dao.list(tx, marker, fetchLimit);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        for (EventProcessor p : processors) {
            p.process(tx, events);
        }

        return events;
    }

    @Named
    public static class AnsibleEventDao extends AbstractDao {

        private final ObjectMapper objectMapper;

        @Inject
        public AnsibleEventDao(@MainDB Configuration cfg, ObjectMapper objectMapper) {
            super(cfg);
            this.objectMapper = objectMapper;
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        private static SelectConditionStep<Record1<JSONB>> payloadField(DSLContext tx, String... keys) {
            return tx.select(function("jsonb_object_agg", JSONB.class, field("key"), field("value")))
                    .from(table("jsonb_each(pe.EVENT_DATA)"))
                    .where(field("key").in(Arrays.asList(keys)));
        }

        public List<EventProcessor.Event> list(DSLContext tx, EventMarkerDao.EventMarker marker, int count) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");

            SelectConditionStep<Record6<UUID, OffsetDateTime, Long, OffsetDateTime, String, JSONB>> q = tx.select(
                            pe.INSTANCE_ID,
                            pe.INSTANCE_CREATED_AT,
                            pe.EVENT_SEQ,
                            pe.EVENT_DATE,
                            pe.EVENT_TYPE,
                            when(pe.EVENT_TYPE.eq(Constants.ANSIBLE_EVENT_TYPE), payloadField(tx, "host", "hostGroup", "status", "duration", "ignore_errors", "currentRetryCount", "hostStatus", "playId", "playbookId", "parentCorrelationId", "action", "isHandler", "taskId", "task"))
                                    .when(pe.EVENT_TYPE.eq(Constants.ANSIBLE_PLAYBOOK_INFO), payloadField(tx, "plays", "playbookId", "playbook", "uniqueHosts", "totalWork", "parentCorrelationId", "currentRetryCount"))
                                    .when(pe.EVENT_TYPE.eq(Constants.ANSIBLE_PLAYBOOK_RESULT), payloadField(tx, "playbookId", "status", "parentCorrelationId")))
                    .from(pe)
                    .where(pe.EVENT_TYPE.in(Constants.ANSIBLE_EVENT_TYPE, Constants.ANSIBLE_PLAYBOOK_INFO, Constants.ANSIBLE_PLAYBOOK_RESULT)
                            .and(pe.EVENT_SEQ.greaterThan(marker.eventSeq())));

            return q.orderBy(pe.EVENT_SEQ)
                    .limit(count)
                    .fetch(r -> ImmutableEvent.builder()
                            .instanceId(r.value1())
                            .instanceCreatedAt(r.value2())
                            .eventSeq(r.value3())
                            .eventDate(r.value4())
                            .eventType(r.value5())
                            .payload(deserialize(r.value6()))
                            .build());
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> deserialize(JSONB o) {
            if (o == null) {
                return null;
            }

            try {
                return objectMapper.readValue(o.toString(), Map.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
