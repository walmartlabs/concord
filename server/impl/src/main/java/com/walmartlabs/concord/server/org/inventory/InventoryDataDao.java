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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.jooq.tables.Inventories;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Inventories.INVENTORIES;
import static com.walmartlabs.concord.server.jooq.tables.InventoryData.INVENTORY_DATA;
import static org.jooq.impl.DSL.*;

@Named
public class InventoryDataDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public InventoryDataDao(@Named("app") Configuration cfg) {
        super(cfg);
        this.objectMapper = new ObjectMapper();
    }

    public List<InventoryDataItem> get(UUID inventoryId, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, inventoryId, path);
        }
    }

    public void merge(UUID inventoryId, String itemPath, Object data) {
        tx(tx -> merge(tx, inventoryId, itemPath, data));
    }

    public void delete(UUID inventoryId, String itemPath) {
        tx(tx -> delete(tx, inventoryId, itemPath));
    }

    public List<Map<String,Object>> list(UUID inventoryId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(INVENTORY_DATA.ITEM_PATH, INVENTORY_DATA.ITEM_DATA.cast(String.class))
                    .from(INVENTORY_DATA)
                    .where(INVENTORY_DATA.INVENTORY_ID.eq(inventoryId))
                    .fetch(this::toListItem);
        }
    }

    private Map<String, Object> toListItem(Record2<String, String> r) {
        Map<String, Object> result = new HashMap<>();
        result.put("path", r.value1());
        result.put("data", deserialize(r.value2()));
        return result;
    }

    private List<InventoryDataItem> get(DSLContext tx, UUID inventoryId, String path) {
        Table<Record> nodes = table("nodes");
        Inventories i1 = INVENTORIES.as("i1");
        Inventories i2 = INVENTORIES.as("i2");

        SelectConditionStep<Record3<UUID, UUID, Integer>> s1 =
                select(i1.INVENTORY_ID, i1.PARENT_INVENTORY_ID, value(1))
                        .from(i1)
                        .where(i1.INVENTORY_ID.eq(inventoryId));

        SelectConditionStep<Record3<UUID, UUID, Integer>> s2 =
                select(i2.INVENTORY_ID, i2.PARENT_INVENTORY_ID, level().add(1))
                        .from(i2, nodes)
                        .where(i2.INVENTORY_ID.eq(INVENTORIES.as("nodes").PARENT_INVENTORY_ID));

        SelectConditionStep<Record3<String, String, Integer>> s = tx.withRecursive("nodes", INVENTORIES.INVENTORY_ID.getName(), INVENTORIES.PARENT_INVENTORY_ID.getName(), "level")
                .as(s1.unionAll(s2))
                .select(INVENTORY_DATA.ITEM_PATH, INVENTORY_DATA.ITEM_DATA.cast(String.class), level())
                .from(INVENTORY_DATA, nodes)
                .where(INVENTORY_DATA.INVENTORY_ID.eq(INVENTORIES.as("nodes").INVENTORY_ID)
                        .and(INVENTORY_DATA.ITEM_PATH.startsWith(path)));
        return s.fetch(this::toEntry);
    }

    private void merge(DSLContext tx, UUID inventoryId, String itemPath, Object data) {
        tx.insertInto(INVENTORY_DATA)
                .columns(INVENTORY_DATA.INVENTORY_ID, INVENTORY_DATA.ITEM_PATH, INVENTORY_DATA.ITEM_DATA)
                .values(value(inventoryId), value(itemPath), field("?::jsonb", serialize(data)))
                .onDuplicateKeyUpdate()
                .set(INVENTORY_DATA.ITEM_DATA, field("?::jsonb", String.class, serialize(data)))
                .execute();
    }

    private void delete(DSLContext tx, UUID inventoryId, String itemPath) {
        tx.deleteFrom(INVENTORY_DATA)
                .where(INVENTORY_DATA.INVENTORY_ID.eq(inventoryId)
                        .and(INVENTORY_DATA.ITEM_PATH.eq(itemPath)))
                .execute();
    }

    private String serialize(Object m) {
        if (m == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    private InventoryDataItem toEntry(Record3<String, String, Integer> r) {
        return new InventoryDataItem(r.value1(), r.value3(), deserialize(r.value2()));
    }
}
