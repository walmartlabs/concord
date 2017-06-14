package com.walmartlabs.concord.server.project.kv;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;

@Named
public class KvDao extends AbstractDao {

    @Inject
    public KvDao(Configuration cfg) {
        super(cfg);

        switch (cfg.dialect()) {
            case POSTGRES:
            case POSTGRES_9_5: {
                break;
            }
            default:
                throw new IllegalStateException("Unsupported DB dialect: " + cfg.dialect());
        }
    }

    public void remove(String projectName, String key) {
        tx(tx -> tx.deleteFrom(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                        .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                .execute());
    }

    public synchronized void put(String projectName, String key, String value) {
        tx(tx -> {
            int rows = tx.insertInto(PROJECT_KV_STORE)
                    .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_STRING)
                    .values(projectName, key, value)
                    .onConflict(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY)
                    .doUpdate().set(PROJECT_KV_STORE.VALUE_STRING, value)
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        });
    }

    public String getString(String projectName, String key) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECT_KV_STORE.VALUE_STRING)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .fetchOne(PROJECT_KV_STORE.VALUE_STRING);
        }
    }

    public Long getLong(String projectName, String key) {
        try (DSLContext tx = DSL.using(cfg)) {
            Record1<Long> r = tx.select(PROJECT_KV_STORE.VALUE_LONG)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            return r.value1();
        }
    }

    public synchronized long inc(String projectName, String key) {
        return txResult(tx -> {
            // grab a lock, it will be released when the transaction ends
            tx.execute("select from pg_advisory_xact_lock(?)", hash(projectName, key));

            // "upsert" the record
            tx.insertInto(PROJECT_KV_STORE)
                    .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_LONG)
                    .values(projectName, key, 1L)
                    .onConflict(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY)
                    .doUpdate().set(PROJECT_KV_STORE.VALUE_LONG, PROJECT_KV_STORE.VALUE_LONG.plus(1))
                    .execute();

            // get an updated value
            return tx.select(PROJECT_KV_STORE.VALUE_LONG)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .fetchOne(PROJECT_KV_STORE.VALUE_LONG);
        });
    }

    private static long hash(String projectName, String key) {
        // should be "good enough" (tm) for advisory locking
        String s = projectName + "/" + key;
        long hash = 7;
        for (int i = 0; i < s.length(); i++) {
            hash = hash * 31 + s.charAt(i);
        }
        return hash;
    }
}
