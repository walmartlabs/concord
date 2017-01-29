package com.walmartlabs.concord.server.history;

import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.history.ProcessHistoryResource;
import org.jooq.Field;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProcessHistory.PROCESS_HISTORY;

@Named
public class ProcessHistoryResourceImpl implements ProcessHistoryResource, Resource {

    private final ProcessHistoryDao historyDao;
    private final Map<String, Field<?>> key2Field;

    @Inject
    public ProcessHistoryResourceImpl(ProcessHistoryDao historyDao) {
        this.historyDao = historyDao;

        this.key2Field = new HashMap<>();
        key2Field.put("instanceId", PROCESS_HISTORY.INSTANCE_ID);
        key2Field.put("createdDt", PROCESS_HISTORY.CREATED_DT);
        key2Field.put("initiator", PROCESS_HISTORY.INITIATOR);
        key2Field.put("lastUpdateDt", PROCESS_HISTORY.LAST_UPDATE_DT);
        key2Field.put("status", PROCESS_HISTORY.CURRENT_STATUS);
    }

    @Override
    public List<ProcessHistoryEntry> list(String sortBy, boolean asc, int limit) {
        Field<?> sortField = null;
        if (sortBy != null) {
            sortField = key2Field.get(sortBy);
        }
        return historyDao.list(limit, sortField, asc);
    }
}
