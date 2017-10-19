package com.walmartlabs.concord.server.process.event;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.process.ProcessEventEntry;
import com.walmartlabs.concord.server.api.process.ProcessEventType;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.value;

@Named
public class EventDao extends AbstractDao {

    @Inject
    public EventDao(Configuration cfg) {
        super(cfg);
    }

    public List<ProcessEventEntry> list(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {

            return tx.select(PROCESS_EVENTS.EVENT_TYPE,
                    PROCESS_EVENTS.EVENT_DATE,
                    PROCESS_EVENTS.EVENT_DATA.cast(String.class))
                    .from(PROCESS_EVENTS)
                    .where(PROCESS_EVENTS.INSTANCE_ID.eq(instanceId))
                    .orderBy(PROCESS_EVENTS.EVENT_DATE)
                    .fetch(EventDao::toEntry);
        }
    }

    public void insert(UUID instanceId, ProcessEventType eventType, Date eventDate, String eventData) {
        tx(tx -> insert(tx, instanceId, eventType, eventDate, eventData));
    }

    public void insert(DSLContext tx, UUID instanceId, ProcessEventType eventType, Date eventDate, String eventData) {
        tx.insertInto(PROCESS_EVENTS)
                .columns(PROCESS_EVENTS.INSTANCE_ID,
                        PROCESS_EVENTS.EVENT_TYPE,
                        PROCESS_EVENTS.EVENT_DATE,
                        PROCESS_EVENTS.EVENT_DATA)
                .values(value(instanceId),
                        value(eventType.name()),
                        value(new Timestamp(eventDate.getTime())),
                        field("?::jsonb", eventData))
                .execute();
    }

    private static ProcessEventEntry toEntry(Record3<String, Timestamp, String> r) {
        return new ProcessEventEntry(ProcessEventType.valueOf(r.value1()), r.value2(), r.value3());
    }
}
