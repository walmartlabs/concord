package com.walmartlabs.concord.server.process.logs;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.Routines.processLogNextRange;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_LOGS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.value;

@Named
public class ProcessLogsDao extends AbstractDao {

    @Inject
    public ProcessLogsDao(Configuration cfg) {
        super(cfg);
    }

    public void append(String instanceId, byte[] data) {
        tx(tx -> tx.insertInto(PROCESS_LOGS)
                .columns(PROCESS_LOGS.INSTANCE_ID, PROCESS_LOGS.CHUNK_RANGE, PROCESS_LOGS.CHUNK_DATA)
                .values(value(instanceId), processLogNextRange(instanceId, data.length), value(data))
                .execute());
    }

    public List<ProcessLogEntry> get(String instanceId, Integer start, Integer end) {
        try (DSLContext tx = DSL.using(cfg)) {
            String lowerBoundExpr = "lower(" + PROCESS_LOGS.CHUNK_RANGE + ")";

            if (start == null && end == null) {
                // entire file
                return tx.select(field(lowerBoundExpr), PROCESS_LOGS.CHUNK_DATA)
                        .from(PROCESS_LOGS)
                        .where(PROCESS_LOGS.INSTANCE_ID.eq(instanceId))
                        .orderBy(PROCESS_LOGS.CHUNK_RANGE)
                        .fetch(ProcessLogsDao::toEntry);

            } else if (start != null) {
                // ranges && [start, end)
                String rangeExpr = PROCESS_LOGS.CHUNK_RANGE.getName() + " && int4range(?, ?)";
                return tx.select(field(lowerBoundExpr), PROCESS_LOGS.CHUNK_DATA)
                        .from(PROCESS_LOGS)
                        .where(PROCESS_LOGS.INSTANCE_ID.eq(instanceId).and(rangeExpr, start, end))
                        .orderBy(PROCESS_LOGS.CHUNK_RANGE)
                        .fetch(ProcessLogsDao::toEntry);

            } else if (end != null) {
                // ranges && [upper_bound - end, upper_bound)
                String rangeExpr = PROCESS_LOGS.CHUNK_RANGE.getName() + " && PROCESS_LOG_LAST_N_BYTES(?, ?)";
                return tx.select(field(lowerBoundExpr), PROCESS_LOGS.CHUNK_DATA)
                        .from(PROCESS_LOGS)
                        .where(PROCESS_LOGS.INSTANCE_ID.eq(instanceId)
                                .and(rangeExpr, instanceId, end))
                        .fetch(ProcessLogsDao::toEntry);
            } else {
                throw new IllegalArgumentException("Invalid range options: start=" + start + ", end=" + end);
            }
        }
    }

    private static ProcessLogEntry toEntry(Record2<Object, byte[]> r) {
        return new ProcessLogEntry((Integer) r.value1(), r.value2());
    }

    public static final class ProcessLogEntry implements Serializable {

        private final int start;
        private final byte[] data;

        public ProcessLogEntry(int start, byte[] data) {
            this.start = start;
            this.data = data;
        }

        public int getStart() {
            return start;
        }

        public byte[] getData() {
            return data;
        }
    }
}
