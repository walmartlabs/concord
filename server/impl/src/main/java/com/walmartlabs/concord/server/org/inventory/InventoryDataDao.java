package com.walmartlabs.concord.server.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.JsonStoreData;
import com.walmartlabs.concord.server.jooq.tables.JsonStores;
import org.jooq.Record;
import org.jooq.*;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.JSON_STORES;
import static com.walmartlabs.concord.server.jooq.Tables.JSON_STORE_DATA;
import static org.jooq.impl.DSL.*;

@Deprecated
public class InventoryDataDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    // TODO shouldn't it be @InventoryDB?
    @Inject
    public InventoryDataDao(@MainDB Configuration cfg,
                            ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    public Object getSingleItem(UUID id, String itemPath) {
        return txResult(tx -> {
            JsonStoreData i = JSON_STORE_DATA.as("i");
            return tx.select(i.ITEM_DATA.cast(String.class))
                    .from(i)
                    .where(i.JSON_STORE_ID.eq(id).and(i.ITEM_PATH.eq(itemPath)))
                    .fetchOne(Record1::value1);
        });
    }

    public List<InventoryDataItem> get(UUID inventoryId, String path) {
        return get(dsl(), inventoryId, path);
    }

    public void merge(UUID inventoryId, String itemPath, Object data) {
        tx(tx -> merge(tx, inventoryId, itemPath, data));
    }

    public void delete(UUID inventoryId, String itemPath) {
        tx(tx -> delete(tx, inventoryId, itemPath));
    }

    public List<Map<String, Object>> list(UUID inventoryId) {
        return dsl().select(JSON_STORE_DATA.ITEM_PATH, JSON_STORE_DATA.ITEM_DATA)
                .from(JSON_STORE_DATA)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(inventoryId))
                .fetch(this::toListItem);
    }

    private Map<String, Object> toListItem(Record2<String, JSONB> r) {
        Map<String, Object> result = new HashMap<>();
        result.put("path", r.value1());
        result.put("data", objectMapper.fromJSONB(r.value2()));
        return result;
    }

    private List<InventoryDataItem> get(DSLContext tx, UUID inventoryId, String path) {
        Table<Record> nodes = table("nodes");
        JsonStores i1 = JSON_STORES.as("i1");
        JsonStores i2 = JSON_STORES.as("i2");

        SelectConditionStep<Record3<UUID, UUID, Integer>> s1 =
                select(i1.JSON_STORE_ID, i1.PARENT_INVENTORY_ID, value(1))
                        .from(i1)
                        .where(i1.JSON_STORE_ID.eq(inventoryId));

        Field<Integer> levelField = field("level", Integer.class);

        SelectConditionStep<Record3<UUID, UUID, Integer>> s2 =
                select(i2.JSON_STORE_ID, i2.PARENT_INVENTORY_ID, levelField.add(1))
                        .from(i2, nodes)
                        .where(i2.JSON_STORE_ID.eq(JSON_STORES.as("nodes").PARENT_INVENTORY_ID));

        SelectConditionStep<Record3<String, JSONB, Integer>> s = tx.withRecursive("nodes", JSON_STORES.JSON_STORE_ID.getName(), JSON_STORES.PARENT_INVENTORY_ID.getName(), levelField.getName())
                .as(s1.unionAll(s2))
                .select(JSON_STORE_DATA.ITEM_PATH, JSON_STORE_DATA.ITEM_DATA, levelField.add(1))
                .from(JSON_STORE_DATA, nodes)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(JSON_STORES.as("nodes").JSON_STORE_ID)
                        .and(JSON_STORE_DATA.ITEM_PATH.startsWith(path)));
        return s.fetch(this::toEntry);
    }

    private void merge(DSLContext tx, UUID inventoryId, String itemPath, Object data) {
        tx.insertInto(JSON_STORE_DATA)
                .columns(JSON_STORE_DATA.JSON_STORE_ID, JSON_STORE_DATA.ITEM_PATH, JSON_STORE_DATA.ITEM_DATA)
                .values(inventoryId, itemPath, objectMapper.toJSONB(data))
                .onDuplicateKeyUpdate()
                .set(JSON_STORE_DATA.ITEM_DATA, objectMapper.toJSONB(data))
                .execute();
    }

    private void delete(DSLContext tx, UUID inventoryId, String itemPath) {
        tx.deleteFrom(JSON_STORE_DATA)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(inventoryId)
                        .and(JSON_STORE_DATA.ITEM_PATH.eq(itemPath)))
                .execute();
    }

    private InventoryDataItem toEntry(Record3<String, JSONB, Integer> r) {
        return new InventoryDataItem(r.value1(), r.value3(), objectMapper.fromJSONB(r.value2()));
    }
}
