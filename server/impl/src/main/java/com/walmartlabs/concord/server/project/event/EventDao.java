package com.walmartlabs.concord.server.project.event;

import com.google.common.collect.ImmutableList;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.process.ProcessEventEntry;
import com.walmartlabs.concord.server.jooq.public_.tables.records.ProcessEventRecord;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProcessEvent.PROCESS_EVENT;

@Named
public class EventDao extends AbstractDao {

    @Inject
    public EventDao(Configuration cfg) {
        super(cfg);
    }

    public List<ProcessEventEntry> list(String instanceId) {
        try (DSLContext tx = DSL.using(cfg);
            Stream<ProcessEventRecord> records = tx.selectFrom(PROCESS_EVENT)
                    .where(PROCESS_EVENT.INSTANCE_ID.eq(instanceId))
                    .stream()) {

            ImmutableList.Builder<ProcessEventEntry> result = ImmutableList.builder();
            records.map(r -> new ProcessEventEntry(r.getInstanceId(), r.getEventType(), r.getEventDate(), r.getEventData()))
                    .forEach(result::add);

            return result.build();
        }
    }

    public void insert(String instanceId, int eventType, Date eventDate, String eventData) {
        tx(tx -> insert(tx, instanceId, eventType, eventDate, eventData));
    }

    public void insert(DSLContext tx, String instanceId, int eventType, Date eventDate, String eventData) {
        tx.insertInto(PROCESS_EVENT)
                .columns(PROCESS_EVENT.INSTANCE_ID, PROCESS_EVENT.EVENT_TYPE,
                        PROCESS_EVENT.EVENT_DATE, PROCESS_EVENT.EVENT_DATA)
                .values(instanceId, eventType, new Timestamp(eventDate.getTime()), eventData)
                .execute();
    }
}
