package com.walmartlabs.concord.server.org.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.inventory.InventoryQueryEntry;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.val;

@Named
public class InventoryQueryExecDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    private final InventoryQueryDao inventoryQueryDao;

    @Inject
    public InventoryQueryExecDao(
            @Named("inventory") Configuration cfg,
            InventoryQueryDao inventoryQueryDao) {
        super(cfg);

        this.objectMapper = new ObjectMapper();
        this.inventoryQueryDao = inventoryQueryDao;
    }

    public List<Object> exec(UUID queryId, Map<String, Object> params) {
        InventoryQueryEntry q = inventoryQueryDao.get(queryId);
        if (q == null) {
            return null;
        }

        try (DSLContext tx = DSL.using(cfg)) {
            return tx.resultQuery(q.getText(), val(serialize(params)))
                    .fetch(this::toExecResult);
        }
    }

    private Object toExecResult(Record record) {
        return deserialize((String) record.getValue(0));
    }

    private String serialize(Object m) {
        if (m == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object deserialize(String ab) {
        if (ab == null) {
            return null;
        }

        try {
            return objectMapper.readValue(ab, Object.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
