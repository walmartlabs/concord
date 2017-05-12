package com.walmartlabs.concord.server.history;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProcessHistory.PROCESS_HISTORY;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.value;

@Named
public class ProcessHistoryDao extends AbstractDao {

    @Inject
    public ProcessHistoryDao(Configuration cfg) {
        super(cfg);
    }

    public ProcessHistoryEntry get(String instanceId) {
        try (DSLContext create = DSL.using(cfg)) {

            Record r = create.select().from(PROCESS_HISTORY)
                    .where(PROCESS_HISTORY.INSTANCE_ID.eq(instanceId))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            return toEntry(r);
        }
    }

    public void insertInitial(String instanceId, String projectName, String initiator, String logFileName) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.insertInto(PROCESS_HISTORY)
                    .columns(PROCESS_HISTORY.INSTANCE_ID,
                            PROCESS_HISTORY.PROJECT_NAME,
                            PROCESS_HISTORY.INITIATOR,
                            PROCESS_HISTORY.CREATED_DT,
                            PROCESS_HISTORY.LAST_UPDATE_DT,
                            PROCESS_HISTORY.CURRENT_STATUS,
                            PROCESS_HISTORY.LOG_FILE_NAME)
                    .values(value(instanceId),
                            value(projectName),
                            value(initiator),
                            currentTimestamp(),
                            currentTimestamp(),
                            value(ProcessStatus.STARTING.toString()),
                            value(logFileName))
                    .execute();
        });
    }

    public void update(String instanceId, ProcessStatus processStatus) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            int i = create.update(PROCESS_HISTORY)
                    .set(PROCESS_HISTORY.CURRENT_STATUS, processStatus.toString())
                    .set(PROCESS_HISTORY.LAST_UPDATE_DT, currentTimestamp())
                    .where(PROCESS_HISTORY.INSTANCE_ID.eq(instanceId))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }
        });
    }

    /**
     * Update the timestamp of a process.
     *
     * @param instanceId
     */
    public void touch(String instanceId) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.update(PROCESS_HISTORY)
                    .set(PROCESS_HISTORY.LAST_UPDATE_DT, currentTimestamp())
                    .where(PROCESS_HISTORY.INSTANCE_ID.eq(instanceId))
                    .execute();
        });
    }

    public List<ProcessHistoryEntry> list(int limit, ProcessStatus... filters) {
        return list(limit, null, true, filters);
    }

    public List<ProcessHistoryEntry> list(int limit, Field<?> sortField, boolean asc, ProcessStatus... filters) {
        try (DSLContext create = DSL.using(cfg)) {

            SelectJoinStep<?> query = create.select().from(PROCESS_HISTORY);

            List<String> statuses = toString(filters);
            if (statuses != null) {
                query.where(PROCESS_HISTORY.CURRENT_STATUS.in(statuses));
            }

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            if (limit > 0) {
                query.limit(limit);
            }

            return query.fetch(new EntryMapper());
        }
    }

    private static final class EntryMapper implements RecordMapper<Record, ProcessHistoryEntry> {

        @Override
        public ProcessHistoryEntry map(Record r) {
            return toEntry(r);
        }
    }

    private static ProcessHistoryEntry toEntry(Record r) {
        String instanceId = r.get(PROCESS_HISTORY.INSTANCE_ID);
        String projectName = r.get(PROCESS_HISTORY.PROJECT_NAME);
        Date createdDt = r.get(PROCESS_HISTORY.CREATED_DT);
        String initiator = r.get(PROCESS_HISTORY.INITIATOR);
        ProcessStatus status = ProcessStatus.valueOf(r.get(PROCESS_HISTORY.CURRENT_STATUS));
        Date lastUpdateDt = r.get(PROCESS_HISTORY.LAST_UPDATE_DT);
        String logFileName = r.get(PROCESS_HISTORY.LOG_FILE_NAME);
        return new ProcessHistoryEntry(instanceId, projectName, createdDt, initiator, status, lastUpdateDt, logFileName);
    }

    private static List<String> toString(ProcessStatus... as) {
        if (as == null || as.length == 0) {
            return null;
        }

        List<String> l = new ArrayList<>(as.length);
        for (ProcessStatus s : as) {
            l.add(s.toString());
        }

        return l;
    }
}
