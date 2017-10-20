package com.walmartlabs.concord.server.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.inventory.InventoryQueryEntry;
import com.walmartlabs.concord.server.jooq.tables.InventoryQueries;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Inventories.INVENTORIES;
import static com.walmartlabs.concord.server.jooq.tables.InventoryQueries.INVENTORY_QUERIES;
import static org.jooq.impl.DSL.*;

@Named
public class InventoryQueryDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public InventoryQueryDao(
            @Named("inventory") Configuration cfg) {
        super(cfg);
        this.objectMapper = new ObjectMapper();
    }

    public UUID getId(UUID inventoryId, String queryName) {
        try(DSLContext tx = DSL.using(cfg)) {
            return tx.select(INVENTORY_QUERIES.QUERY_ID)
                    .from(INVENTORY_QUERIES)
                    .where(INVENTORY_QUERIES.QUERY_NAME.eq(queryName)
                            .and(INVENTORY_QUERIES.INVENTORY_ID.eq(inventoryId)))
                    .fetchOne(INVENTORY_QUERIES.QUERY_ID);
        }
    }

    public InventoryQueryEntry get(UUID queryId) {
        try (DSLContext tx = DSL.using(cfg)) {
            InventoryQueries q = INVENTORY_QUERIES.as("q");
            Field<String> inventoryNameField = select(INVENTORIES.INVENTORY_NAME).from(INVENTORIES).where(INVENTORIES.INVENTORY_ID.eq(q.INVENTORY_ID)).asField();

            Record4<UUID, String, String, String> r =
                    tx.select(
                        q.QUERY_ID,
                        q.QUERY_NAME,
                        inventoryNameField,
                        q.QUERY_TEXT)
                    .from(q)
                    .where(q.QUERY_ID.eq(queryId))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            return new InventoryQueryEntry(r.get(q.QUERY_ID), r.get(q.QUERY_NAME), r.get(inventoryNameField), r.get(q.QUERY_TEXT));
        }
    }

    public UUID insert(UUID inventoryId, String queryName, String text) {
        return txResult(tx -> insert(tx, inventoryId, queryName, text));
    }

    public void update(UUID queryId, UUID inventoryId, String queryName, String text) {
        tx(tx -> update(tx, queryId, inventoryId, queryName, text));
    }

    public void delete(UUID queryId) {
        tx(tx -> delete(tx, queryId));
    }

    public List<Object> exec(UUID queryId, Map<String, Object> params) {
        InventoryQueryEntry q = get(queryId);
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

    private UUID insert(DSLContext tx, UUID inventoryId, String queryName, String text) {
        return tx.insertInto(INVENTORY_QUERIES)
                .columns(INVENTORY_QUERIES.INVENTORY_ID, INVENTORY_QUERIES.QUERY_NAME, INVENTORY_QUERIES.QUERY_TEXT)
                .values(value(inventoryId), value(queryName), value(text))
                .returning(INVENTORY_QUERIES.QUERY_ID)
                .fetchOne()
                .getQueryId();

    }

    private void update(DSLContext tx, UUID queryId, UUID inventoryId, String queryName, String text) {
        tx.update(INVENTORY_QUERIES)
                .set(INVENTORY_QUERIES.QUERY_NAME, value(queryName))
                .set(INVENTORY_QUERIES.INVENTORY_ID, value(inventoryId))
                .set(INVENTORY_QUERIES.QUERY_TEXT, value(text))
                .where(INVENTORY_QUERIES.QUERY_ID.eq(queryId))
                .execute();
    }

    private void delete(DSLContext tx, UUID queryId) {
        tx.deleteFrom(INVENTORY_QUERIES)
                .where(INVENTORY_QUERIES.QUERY_ID.eq(queryId))
                .execute();
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
