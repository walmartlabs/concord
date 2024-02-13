package com.walmartlabs.concord.server.org.jsonstore;

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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.JsonStoreData;
import org.jooq.*;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.JsonStoreData.JSON_STORE_DATA;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.sum;

public class JsonStoreDataDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public JsonStoreDataDao(@MainDB Configuration cfg,
                            ConcordObjectMapper objectMapper) {

        super(cfg);
        this.objectMapper = objectMapper;
    }

    public Long getItemSize(UUID storeId, String itemPath) {
        return dsl().select(JSON_STORE_DATA.ITEM_DATA_SIZE)
                .from(JSON_STORE_DATA)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(storeId)
                        .and(JSON_STORE_DATA.ITEM_PATH.eq(itemPath)))
                .fetchOne(JSON_STORE_DATA.ITEM_DATA_SIZE);
    }

    public Object get(UUID storeId, String itemPath) {
        return txResult(tx -> {
            JsonStoreData i = JSON_STORE_DATA.as("i");
            return tx.select(i.ITEM_DATA.cast(String.class))
                    .from(i)
                    .where(i.JSON_STORE_ID.eq(storeId).and(i.ITEM_PATH.eq(itemPath)))
                    .fetchOne(Record1::value1);
        });
    }

    public List<JsonStoreDataEntry> list(UUID storeId) {
        return dsl().select(JSON_STORE_DATA.ITEM_PATH, JSON_STORE_DATA.ITEM_DATA)
                .from(JSON_STORE_DATA)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(storeId))
                .fetch(this::toDataEntry);
    }

    public List<String> listPath(UUID storeId, int offset, int limit, String filter) {
        SelectJoinStep<Record1<String>> q = dsl().select(JSON_STORE_DATA.ITEM_PATH)
                .from(JSON_STORE_DATA);

        if (filter != null) {
            q.where(JSON_STORE_DATA.ITEM_PATH.containsIgnoreCase(filter));
        }

        if (offset >= 0) {
            q.offset(offset);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        return q.where(JSON_STORE_DATA.JSON_STORE_ID.eq(storeId))
                .orderBy(JSON_STORE_DATA.ITEM_PATH)
                .fetch(Record1::value1);
    }

    public void upsert(UUID storeId, String itemPath, String data) {
        tx(tx -> tx.insertInto(JSON_STORE_DATA)
                .columns(JSON_STORE_DATA.JSON_STORE_ID, JSON_STORE_DATA.ITEM_PATH, JSON_STORE_DATA.ITEM_DATA, JSON_STORE_DATA.ITEM_DATA_SIZE)
                .values(storeId, itemPath, objectMapper.jsonStringToJSONB(data), (long) data.length())
                .onDuplicateKeyUpdate()
                .set(JSON_STORE_DATA.ITEM_DATA, objectMapper.jsonStringToJSONB(data))
                .set(JSON_STORE_DATA.ITEM_DATA_SIZE, (long) data.length())
                .execute());
    }

    public Long getSize(UUID storeId) {
        return txResult(tx -> tx.select(coalesce(sum(JSON_STORE_DATA.ITEM_DATA_SIZE), BigDecimal.ZERO))
                .from(JSON_STORE_DATA)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(storeId))
                .fetchOne(r -> r.value1().longValue()));
    }

    public boolean delete(UUID storeId, String itemPath) {
        return txResult(tx -> tx.deleteFrom(JSON_STORE_DATA)
                .where(JSON_STORE_DATA.JSON_STORE_ID.eq(storeId)
                        .and(JSON_STORE_DATA.ITEM_PATH.eq(itemPath)))
                .execute() > 0);
    }

    private JsonStoreDataEntry toDataEntry(Record2<String, JSONB> r) {
        return JsonStoreDataEntry.builder()
                .path(r.value1())
                .data(objectMapper.fromJSONB(r.value2(), Object.class))
                .build();
    }
}
