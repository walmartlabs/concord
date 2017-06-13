package com.walmartlabs.concord.server.project.event;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.process.ProcessEventEntry;
import com.walmartlabs.concord.server.api.process.ProcessEventType;
import com.walmartlabs.concord.server.jooq.public_.tables.records.ProcessEventRecord;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProcessEvent.PROCESS_EVENT;

@Named
public class EventDao extends AbstractDao {

    @Inject
    public EventDao(Configuration cfg) {
        super(cfg);
    }

    public List<ProcessEventEntry> list(String instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {

            return tx.selectFrom(PROCESS_EVENT)
                    .where(PROCESS_EVENT.INSTANCE_ID.eq(instanceId))
                    .orderBy(PROCESS_EVENT.EVENT_DATE)
                    .fetch(EventDao::toEntry);
        }
    }

    public void insert(String instanceId, ProcessEventType eventType, Date eventDate, String eventData) {
        tx(tx -> insert(tx, instanceId, eventType, eventDate, eventData));
    }

    public void insert(DSLContext tx, String instanceId, ProcessEventType eventType, Date eventDate, String eventData) {
        tx.insertInto(PROCESS_EVENT)
                .columns(PROCESS_EVENT.INSTANCE_ID, PROCESS_EVENT.EVENT_TYPE,
                        PROCESS_EVENT.EVENT_DATE, PROCESS_EVENT.EVENT_DATA)
                .values(instanceId, eventType.name(), new Timestamp(eventDate.getTime()), eventData)
                .execute();
    }

    private static ProcessEventEntry toEntry(ProcessEventRecord r) {
        return new ProcessEventEntry(ProcessEventType.valueOf(r.getEventType()), r.getEventDate(), r.getEventData());
    }
}
