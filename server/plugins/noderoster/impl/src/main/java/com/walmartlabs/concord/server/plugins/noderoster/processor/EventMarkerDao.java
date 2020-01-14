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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.jooq.tables.EventProcessorMarker;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record1;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.value;

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

    public EventMarker get(String processorName) {
        EventProcessorMarker m = Tables.EVENT_PROCESSOR_MARKER.as("m");

        Long currentEventSeq = txResult(tx -> tx.select(m.EVENT_SEQ)
                .from(m)
                .where(m.PROCESSOR_NAME.eq(processorName))
                .fetchOne(m.EVENT_SEQ));

        Long maxEventSeq = txResult(tx -> tx.select(max(PROCESS_EVENTS.EVENT_SEQ))
                .from(PROCESS_EVENTS)
                .fetchOne(Record1::value1));

        return EventMarker.builder()
                .eventSeq(currentEventSeq != null ? currentEventSeq : -1)
                .maxEventSeq(maxEventSeq != null ? maxEventSeq : -1)
                .build();
    }

    public void update(DSLContext tx, String processorName, long eventSeq) {
        EventProcessorMarker m = Tables.EVENT_PROCESSOR_MARKER.as("m");
        tx.insertInto(m)
                .columns(m.PROCESSOR_NAME, m.EVENT_SEQ)
                .values(value(processorName), value(eventSeq))
                .onDuplicateKeyUpdate()
                .set(m.EVENT_SEQ, eventSeq)
                .where(m.PROCESSOR_NAME.eq(processorName))
                .execute();
    }

    @Value.Immutable
    public interface EventMarker {

        long eventSeq();

        long maxEventSeq();

        static ImmutableEventMarker.Builder builder() {
            return ImmutableEventMarker.builder();
        }
    }
}
