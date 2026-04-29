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
import com.walmartlabs.concord.server.UuidGenerator;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.SelectJoinStep;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.JsonStoreQueries.JSON_STORE_QUERIES;
import static java.util.Objects.requireNonNull;
import static org.jooq.impl.DSL.value;

public class JsonStoreQueryDao extends AbstractDao {

    private final UuidGenerator uuidGenerator;

    @Inject
    public JsonStoreQueryDao(@MainDB Configuration cfg, UuidGenerator uuidGenerator) {
        super(cfg);
        this.uuidGenerator = requireNonNull(uuidGenerator);
    }

    public UUID getId(UUID storeId, String queryName) {
        return dsl().select(JSON_STORE_QUERIES.QUERY_ID)
                .from(JSON_STORE_QUERIES)
                .where(JSON_STORE_QUERIES.JSON_STORE_ID.eq(storeId)
                        .and(JSON_STORE_QUERIES.QUERY_NAME.eq(queryName)))
                .fetchOne(JSON_STORE_QUERIES.QUERY_ID);
    }

    public JsonStoreQueryEntry get(UUID storeId, String queryName) {
        Record4<UUID, String, UUID, String> r = createSelect(dsl())
                .where(JSON_STORE_QUERIES.JSON_STORE_ID.eq(storeId)
                        .and(JSON_STORE_QUERIES.QUERY_NAME.eq(queryName)))
                .fetchOne();

        if (r == null) {
            return null;
        }

        return toEntry(r);
    }

    public JsonStoreQueryEntry get(UUID queryId) {
        return createSelect(dsl())
                .where(JSON_STORE_QUERIES.QUERY_ID.eq(queryId))
                .fetchOne(JsonStoreQueryDao::toEntry);
    }

    public List<JsonStoreQueryEntry> list(UUID storeId, int offset, int limit, String filter) {
        SelectJoinStep<Record4<UUID, String, UUID, String>> q = createSelect(dsl());

        if (filter != null) {
            q.where(JSON_STORE_QUERIES.QUERY_NAME.containsIgnoreCase(filter));
        }

        if (offset >= 0) {
            q.offset(offset);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        return q
                .where(JSON_STORE_QUERIES.JSON_STORE_ID.eq(storeId))
                .orderBy(JSON_STORE_QUERIES.QUERY_NAME)
                .fetch(JsonStoreQueryDao::toEntry);
    }

    public UUID insert(UUID storeId, String queryName, String text) {
        return txResult(tx -> insert(tx, storeId, queryName, text));
    }

    public void update(UUID queryId, String text) {
        tx(tx -> update(tx, queryId, text));
    }

    public void delete(UUID storeId, String queryName) {
        tx(tx -> delete(tx, storeId, queryName));
    }

    private UUID insert(DSLContext tx, UUID storeId, String queryName, String text) {
        UUID queryId = uuidGenerator.generate();
        return tx.insertInto(JSON_STORE_QUERIES)
                .columns(JSON_STORE_QUERIES.QUERY_ID, JSON_STORE_QUERIES.JSON_STORE_ID, JSON_STORE_QUERIES.QUERY_NAME, JSON_STORE_QUERIES.QUERY_TEXT)
                .values(value(queryId), value(storeId), value(queryName), value(text))
                .returning(JSON_STORE_QUERIES.QUERY_ID)
                .fetchOne()
                .getQueryId();

    }

    private void update(DSLContext tx, UUID queryId, String text) {
        tx.update(JSON_STORE_QUERIES)
                .set(JSON_STORE_QUERIES.QUERY_TEXT, value(text))
                .where(JSON_STORE_QUERIES.QUERY_ID.eq(queryId))
                .execute();
    }

    private void delete(DSLContext tx, UUID storeId, String queryName) {
        tx.deleteFrom(JSON_STORE_QUERIES)
                .where(JSON_STORE_QUERIES.JSON_STORE_ID.eq(storeId).and(JSON_STORE_QUERIES.QUERY_NAME.eq(queryName)))
                .execute();
    }

    private static JsonStoreQueryEntry toEntry(Record4<UUID, String, UUID, String> r) {
        return JsonStoreQueryEntry.builder()
                .id(r.get(JSON_STORE_QUERIES.QUERY_ID))
                .name(r.get(JSON_STORE_QUERIES.QUERY_NAME))
                .storeId(r.get(JSON_STORE_QUERIES.JSON_STORE_ID))
                .text(r.get(JSON_STORE_QUERIES.QUERY_TEXT))
                .build();
    }

    private static SelectJoinStep<Record4<UUID, String, UUID, String>> createSelect(DSLContext tx) {
        return tx.select(JSON_STORE_QUERIES.QUERY_ID,
                JSON_STORE_QUERIES.QUERY_NAME,
                JSON_STORE_QUERIES.JSON_STORE_ID,
                JSON_STORE_QUERIES.QUERY_TEXT)
                .from(JSON_STORE_QUERIES);
    }
}
