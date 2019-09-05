package com.walmartlabs.concord.server.org.project;

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
import com.walmartlabs.concord.server.jooq.tables.ProjectKvStore;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;

@Named
public class KvDao extends AbstractDao {

    @Inject
    public KvDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public void remove(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        tx(tx -> tx.deleteFrom(kv)
                .where(kv.PROJECT_ID.eq(projectId)
                        .and(kv.VALUE_KEY.eq(key)))
                .execute());
    }

    public synchronized void putString(UUID projectId, String key, String value) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        tx(tx -> {
            int rows = tx.insertInto(kv)
                    .columns(kv.PROJECT_ID, kv.VALUE_KEY, kv.VALUE_STRING)
                    .values(projectId, key, value)
                    .onConflict(kv.PROJECT_ID, kv.VALUE_KEY)
                    .doUpdate().set(kv.VALUE_STRING, value)
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        });
    }

    public synchronized void putLong(UUID projectId, String key, long value) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        tx(tx -> {
            int rows = tx.insertInto(kv)
                    .columns(kv.PROJECT_ID, kv.VALUE_KEY, kv.VALUE_LONG)
                    .values(projectId, key, value)
                    .onConflict(kv.PROJECT_ID, kv.VALUE_KEY)
                    .doUpdate().set(kv.VALUE_LONG, value)
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        });
    }

    public String getString(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(kv.VALUE_STRING)
                    .from(kv)
                    .where(kv.PROJECT_ID.eq(projectId)
                            .and(kv.VALUE_KEY.eq(key)))
                    .fetchOne(kv.VALUE_STRING);
        }
    }

    public Long getLong(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        try (DSLContext tx = DSL.using(cfg)) {
            Record1<Long> r = tx.select(kv.VALUE_LONG)
                    .from(kv)
                    .where(kv.PROJECT_ID.eq(projectId)
                            .and(kv.VALUE_KEY.eq(key)))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            return r.value1();
        }
    }

    public synchronized long inc(UUID projectId, String key) {
        ProjectKvStore kv = PROJECT_KV_STORE.as("kv");
        return txResult(tx -> {
            // grab a lock, it will be released when the transaction ends
            tx.execute("select from pg_advisory_xact_lock(?)", hash(projectId, key));

            // "upsert" the record
            tx.insertInto(kv)
                    .columns(kv.PROJECT_ID, kv.VALUE_KEY, kv.VALUE_LONG)
                    .values(projectId, key, 1L)
                    .onConflict(kv.PROJECT_ID, kv.VALUE_KEY)
                    .doUpdate().set(kv.VALUE_LONG, kv.VALUE_LONG.plus(1))
                    .execute();

            // get an updated value
            return tx.select(kv.VALUE_LONG)
                    .from(kv)
                    .where(kv.PROJECT_ID.eq(projectId)
                            .and(kv.VALUE_KEY.eq(key)))
                    .fetchOne(kv.VALUE_LONG);
        });
    }

    private static long hash(UUID projectId, String key) {
        // should be "good enough" (tm) for advisory locking
        String s = projectId + "/" + key;
        long hash = 7;
        for (int i = 0; i < s.length(); i++) {
            hash = hash * 31 + s.charAt(i);
        }
        return hash;
    }
}
