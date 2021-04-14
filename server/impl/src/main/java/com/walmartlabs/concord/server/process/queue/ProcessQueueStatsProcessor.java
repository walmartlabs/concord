package com.walmartlabs.concord.server.process.queue;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.cfg.ProcessQueueConfiguration;
import com.walmartlabs.concord.server.jooq.tables.EventProcessorMarker;
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.immutables.value.Value;
import org.jooq.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.db.PgUtils.jsonbText;
import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.process.queue.ProcessQueueStatsProcessor.EventMarkerDao.ProcessStatusEventMarker;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.value;

@Named("process-queue-statistics-processor")
@Singleton
public class ProcessQueueStatsProcessor implements ScheduledTask {

    private static final String PROCESSOR_NAME = "process-queue-statistics-processor";

    private final ProcessQueueConfiguration cfg;
    private final EventMarkerDao eventMarkerDao;
    private final Dao dao;

    @Inject
    public ProcessQueueStatsProcessor(ProcessQueueConfiguration cfg,
                                      EventMarkerDao eventMarkerDao,
                                      Dao dao) {
        this.cfg = cfg;
        this.eventMarkerDao = eventMarkerDao;
        this.dao = dao;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getRefreshInterval() != null ? cfg.getRefreshInterval().getSeconds() : 0;
    }

    @Override
    public void performTask() throws Exception {
        int processedEvents;

        do {
            ProcessStatusEventMarker m = eventMarkerDao.get(PROCESSOR_NAME);
            processedEvents = process(m, cfg.getFetchLimit());
        } while (processedEvents >= cfg.getFetchLimit());
    }

    private int process(ProcessStatusEventMarker m, int fetchLimit) {
        return eventMarkerDao.txResult(tx -> {
            List<ProcessStatusEvent> events = processEvents(tx, m, fetchLimit);
            if (events.isEmpty()) {
                eventMarkerDao.update(tx, PROCESSOR_NAME, m.maxEventSeq());
                return 0;
            }

            ProcessStatusEvent lastEvent = events.get(events.size() - 1);
            eventMarkerDao.update(tx, PROCESSOR_NAME, lastEvent.eventSeq());

            return events.size();
        });
    }

    private List<ProcessStatusEvent> processEvents(DSLContext tx, ProcessStatusEventMarker marker, int fetchLimit) {
        List<ProcessStatusEvent> events = dao.listEvents(tx, marker, fetchLimit);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> statusCounters = new HashMap<>();
        for (ProcessStatusEvent e : events) {
            if (e.oldStatus() != null) {
                statusCounters.compute(e.oldStatus(), (k, v) -> (v == null) ? -1 : v - 1);
            }
            statusCounters.compute(e.newStatus(), (k, v) -> (v == null) ? 1 : v + 1);
        }

        dao.updateCounters(tx, statusCounters);

        return events;
    }

    @Value.Immutable
    public interface ProcessStatusEvent {

        long eventSeq();

        @Nullable
        String oldStatus();

        String newStatus();

        static ImmutableProcessStatusEvent.Builder builder() {
            return ImmutableProcessStatusEvent.builder();
        }
    }

    @Named
    static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public List<ProcessStatusEvent> listEvents(DSLContext tx, ProcessStatusEventMarker marker, int count) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");

            SelectConditionStep<Record3<Long, String, String>> q = tx.select(
                    pe.EVENT_SEQ,
                    jsonbText(pe.EVENT_DATA, "oldStatus"),
                    jsonbText(pe.EVENT_DATA, "newStatus"))
                    .from(pe)
                    .where(pe.EVENT_TYPE.eq(EventType.PROCESS_STATUS.name())
                            .and(pe.EVENT_SEQ.greaterThan(marker.eventSeq()))
                            .and(jsonbText(pe.EVENT_DATA, "newStatus").isNotNull()));

            return q.orderBy(pe.EVENT_SEQ)
                    .limit(count)
                    .fetch(r -> ProcessStatusEvent.builder()
                            .eventSeq(r.value1())
                            .oldStatus(r.value2())
                            .newStatus(r.value3())
                            .build());
        }

        public void updateCounters(DSLContext tx, Map<String, Integer> counters) {
            counters = counters.entrySet().stream()
                    .filter(e -> e.getValue() != 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (counters.isEmpty()) {
                return ;
            }

            if (counters.size() == 1) {
                Map.Entry<String, Integer> e = counters.entrySet().iterator().next();

                tx.update(PROCESS_QUEUE_STATS)
                        .set(PROCESS_QUEUE_STATS.PROCESS_COUNT, PROCESS_QUEUE_STATS.PROCESS_COUNT.plus(e.getValue()))
                        .where(PROCESS_QUEUE_STATS.STATUS.eq(e.getKey()))
                        .execute();
            } else {
                BatchBindStep q = tx.batch(tx.update(PROCESS_QUEUE_STATS)
                        .set(PROCESS_QUEUE_STATS.PROCESS_COUNT, PROCESS_QUEUE_STATS.PROCESS_COUNT.plus(0))
                        .where(PROCESS_QUEUE_STATS.STATUS.eq((String)null)));

                for (Map.Entry<String, Integer> e : counters.entrySet()) {
                    q.bind(value(e.getValue()), value(e.getKey()));
                }

                q.execute();
            }
        }
    }

    @Named
    static class EventMarkerDao extends AbstractDao {

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

        public ProcessStatusEventMarker get(String processorName) {
            EventProcessorMarker m = EVENT_PROCESSOR_MARKER.as("m");

            long currentEventSeq = txResult(tx -> tx.select(m.EVENT_SEQ)
                    .from(m)
                    .where(m.PROCESSOR_NAME.eq(processorName))
                    .fetchOne(m.EVENT_SEQ));

            Long maxEventSeq = txResult(tx -> tx.select(max(PROCESS_EVENTS.EVENT_SEQ))
                    .from(PROCESS_EVENTS)
                    .fetchOne(Record1::value1));

            return ProcessStatusEventMarker.builder()
                    .eventSeq(currentEventSeq)
                    .maxEventSeq(maxEventSeq != null ? maxEventSeq : -1)
                    .build();
        }

        public void update(DSLContext tx, String processorName, long eventSeq) {
            EventProcessorMarker m = EVENT_PROCESSOR_MARKER.as("m");
            tx.update(m)
                    .set(m.EVENT_SEQ, eventSeq)
                    .where(m.PROCESSOR_NAME.eq(processorName))
                    .execute();
        }

        @Value.Immutable
        interface ProcessStatusEventMarker {

            long eventSeq();

            long maxEventSeq();

            static ImmutableProcessStatusEventMarker.Builder builder() {
                return ImmutableProcessStatusEventMarker.builder();
            }
        }
    }
}
