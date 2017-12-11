package com.walmartlabs.concord.server.org.inventory;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.inventory.InventoryQueryEntry;
import com.walmartlabs.concord.server.jooq.tables.InventoryQueries;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Inventories.INVENTORIES;
import static com.walmartlabs.concord.server.jooq.tables.InventoryQueries.INVENTORY_QUERIES;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.value;

@Named
public class InventoryQueryDao extends AbstractDao {

    @Inject
    public InventoryQueryDao(Configuration cfg) {
        super(cfg);
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
}
