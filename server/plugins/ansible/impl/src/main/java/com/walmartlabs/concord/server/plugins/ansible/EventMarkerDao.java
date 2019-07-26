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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.jooq.tables.EventProcessorMarkers;
import com.walmartlabs.concord.server.jooq.tables.ProcessEventStats;
import org.immutables.value.Value;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.Tables.EVENT_PROCESSOR_MARKERS;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENT_STATS;
import static org.jooq.impl.DSL.*;

// TODO: move to plugins sdk?
@Named
public class EventMarkerDao extends AbstractDao {

    @Inject
    public EventMarkerDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public List<EventMarker> get(String processorName) {
        ProcessEventStats e = PROCESS_EVENT_STATS.as("e");
        EventProcessorMarkers m = EVENT_PROCESSOR_MARKERS.as("m");

        Field<Timestamp> instanceCreatedEnd = e.INSTANCE_CREATED_DATE.plus(PgUtils.interval("1 day"));
        Field<Long> markerEventSeq = coalesce(m.EVENT_SEQ, -1L);
        Field<Long> eventSeq = least(e.MAX_EVENT_SEQ, markerEventSeq);

        return txResult(tx -> tx.select(e.INSTANCE_CREATED_DATE, instanceCreatedEnd, e.MAX_EVENT_SEQ, eventSeq)
                .from(e)
                .leftJoin(m).on(m.PROCESSOR_NAME.eq(processorName)
                        .and(m.INSTANCE_CREATED_DATE.eq(e.INSTANCE_CREATED_DATE)))
                .where(e.MAX_EVENT_SEQ.greaterThan(markerEventSeq))
                .fetch(EventMarkerDao::toMarker));
    }

    public void update(DSLContext tx, String processorName, Timestamp instanceCreatedStart, long eventSeq) {
        EventProcessorMarkers m = EVENT_PROCESSOR_MARKERS.as("m");
        tx.insertInto(m)
                .columns(m.PROCESSOR_NAME, m.EVENT_SEQ, m.INSTANCE_CREATED_DATE)
                .values(value(processorName), value(eventSeq), value(instanceCreatedStart))
                .onDuplicateKeyUpdate()
                .set(m.EVENT_SEQ, eventSeq)
                .where(m.PROCESSOR_NAME.eq(processorName)
                        .and(m.INSTANCE_CREATED_DATE.eq(instanceCreatedStart)))
                .execute();
    }

    public void cleanup(String processorName) {
        EventProcessorMarkers m = EVENT_PROCESSOR_MARKERS.as("m");

        tx(tx -> tx.deleteFrom(m)
                .where(m.PROCESSOR_NAME.eq(processorName)
                        .and(m.INSTANCE_CREATED_DATE.notIn(
                                tx.select(PROCESS_EVENT_STATS.INSTANCE_CREATED_DATE)
                                        .from(PROCESS_EVENT_STATS))))
                .execute());
    }

    private static EventMarker toMarker(Record4<Timestamp, Timestamp, Long, Long> r) {
        return EventMarker.builder()
                .instanceCreatedStart(r.value1())
                .instanceCreatedEnd(r.value2())
                .maxEventSeq(r.value3())
                .eventSeq(r.value4())
                .build();
    }

    @Value.Immutable
    public interface EventMarker {

        Timestamp instanceCreatedStart();

        Timestamp instanceCreatedEnd();

        long eventSeq();

        long maxEventSeq();

        static ImmutableEventMarker.Builder builder() {
            return ImmutableEventMarker.builder();
        }
    }
}
