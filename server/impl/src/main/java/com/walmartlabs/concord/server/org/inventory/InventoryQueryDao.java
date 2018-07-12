package com.walmartlabs.concord.server.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.InventoryQueries.INVENTORY_QUERIES;
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
            Record4<UUID, String, UUID, String> r = createSelect(tx)
                    .where(INVENTORY_QUERIES.QUERY_ID.eq(queryId))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            return toEntry(r);
        }
    }

    public List<InventoryQueryEntry> list(UUID inventoryId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return createSelect(tx)
                    .where(INVENTORY_QUERIES.INVENTORY_ID.eq(inventoryId))
                    .orderBy(INVENTORY_QUERIES.QUERY_NAME)
                    .fetch(InventoryQueryDao::toEntry);
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

    private static InventoryQueryEntry toEntry(Record4<UUID, String, UUID, String> r) {
        return new InventoryQueryEntry(r.get(INVENTORY_QUERIES.QUERY_ID),
                r.get(INVENTORY_QUERIES.QUERY_NAME),
                r.get(INVENTORY_QUERIES.INVENTORY_ID),
                r.get(INVENTORY_QUERIES.QUERY_TEXT));
    }

    private static SelectJoinStep<Record4<UUID, String, UUID, String>> createSelect(DSLContext tx) {
        return tx.select(INVENTORY_QUERIES.QUERY_ID,
                INVENTORY_QUERIES.QUERY_NAME,
                INVENTORY_QUERIES.INVENTORY_ID,
                INVENTORY_QUERIES.QUERY_TEXT)
                .from(INVENTORY_QUERIES);
    }
}
