package com.walmartlabs.concord.server.org.project;

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
import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.jooq.tables.ProjectKvStore;
import org.jooq.Configuration;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.SelectJoinStep;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.value;

public class KvDao extends AbstractDao {

    private final Locks locks;

    @Inject
    public KvDao(@MainDB Configuration cfg, Locks locks) {
        super(cfg);
        this.locks = locks;
    }

    public void remove(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        tx(tx -> tx.deleteFrom(kv)
                .where(kv.PROJECT_ID.eq(projectId)
                        .and(kv.VALUE_KEY.eq(key)))
                .execute());
    }

    public void putString(UUID projectId, String key, String value) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        tx(tx -> {
            int rows = tx.insertInto(kv)
                    .columns(kv.PROJECT_ID, kv.VALUE_KEY, kv.VALUE_STRING, kv.LAST_UPDATED_AT)
                    .values(value(projectId), value(key), value(value), currentOffsetDateTime())
                    .onConflict(kv.PROJECT_ID, kv.VALUE_KEY)
                    .doUpdate()
                        .set(kv.VALUE_STRING, value)
                        .set(kv.LAST_UPDATED_AT, currentOffsetDateTime())
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        });
    }

    public void putLong(UUID projectId, String key, long value) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        tx(tx -> {
            int rows = tx.insertInto(kv)
                    .columns(kv.PROJECT_ID, kv.VALUE_KEY, kv.VALUE_LONG, kv.LAST_UPDATED_AT)
                    .values(value(projectId), value(key), value(value), currentOffsetDateTime())
                    .onConflict(kv.PROJECT_ID, kv.VALUE_KEY)
                    .doUpdate()
                        .set(kv.VALUE_LONG, value)
                        .set(kv.LAST_UPDATED_AT, currentOffsetDateTime())
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        });
    }

    public String getString(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        return dsl().select(kv.VALUE_STRING)
                .from(kv)
                .where(kv.PROJECT_ID.eq(projectId)
                        .and(kv.VALUE_KEY.eq(key)))
                .fetchOne(kv.VALUE_STRING);
    }

    public Long getLong(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");

        Record1<Long> r = dsl().select(kv.VALUE_LONG)
                .from(kv)
                .where(kv.PROJECT_ID.eq(projectId)
                        .and(kv.VALUE_KEY.eq(key)))
                .fetchOne();

        if (r == null) {
            return null;
        }

        return r.value1();
    }

    public long inc(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        return txResult(tx -> {
            // grab a lock, it will be released when the transaction ends
            locks.lock(tx, projectId + "/" + key);

            // "upsert" the record
            tx.insertInto(kv)
                    .columns(kv.PROJECT_ID, kv.VALUE_KEY, kv.VALUE_LONG, kv.LAST_UPDATED_AT)
                    .values(value(projectId), value(key), value(1L), currentOffsetDateTime())
                    .onConflict(kv.PROJECT_ID, kv.VALUE_KEY)
                    .doUpdate()
                        .set(kv.VALUE_LONG, kv.VALUE_LONG.plus(1))
                        .set(kv.LAST_UPDATED_AT, currentOffsetDateTime())
                    .execute();

            // get an updated value
            return tx.select(kv.VALUE_LONG)
                    .from(kv)
                    .where(kv.PROJECT_ID.eq(projectId)
                            .and(kv.VALUE_KEY.eq(key)))
                    .fetchOne(kv.VALUE_LONG);
        });
    }

    public List<KvEntry> list(UUID projectId, int offset, int limit, String filter) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");

        return txResult(tx -> {
            SelectJoinStep<Record4<String, Long, String, OffsetDateTime>> q = tx.select(kv.VALUE_KEY, kv.VALUE_LONG, kv.VALUE_STRING, kv.LAST_UPDATED_AT)
                    .from(kv);

            if (filter != null) {
                q.where(kv.VALUE_KEY.containsIgnoreCase(filter));
            }

            if (offset > 0) {
                q.offset(offset);
            }

            if (limit > 0) {
                q.limit(limit);
            }

            return q
                    .where(kv.PROJECT_ID.eq(projectId))
                    .orderBy(kv.VALUE_KEY)
                    .fetch(KvDao::toEntry);
        });
    }

    public int count(UUID projectId) {
        return txResult(tx -> tx.selectCount()
                .from(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_ID.eq(projectId))
                .fetchOne(0, int.class));
    }

    public boolean exists(UUID projectId, String key) {
        return txResult(tx -> tx.fetchExists((tx.selectOne()
                .from(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_ID.eq(projectId)
                        .and(PROJECT_KV_STORE.VALUE_KEY.eq(key))))));
    }

    private static KvEntry toEntry(Record4<String, Long, String, OffsetDateTime> r) {
        Object value = r.get(PROJECT_KV_STORE.VALUE_STRING);
        if (value == null) {
            value = r.get(PROJECT_KV_STORE.VALUE_LONG);
        }
        return KvEntry.builder()
                .key(r.get(PROJECT_KV_STORE.VALUE_KEY))
                .value(value)
                .lastUpdatedAt(r.get(PROJECT_KV_STORE.LAST_UPDATED_AT))
                .build();
    }
}
