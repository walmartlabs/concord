package com.walmartlabs.concord.server.project.kv;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectKvStore.PROJECT_KV_STORE;

@Named
public class KvDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(KvDao.class);

    private final Kv delegate;

    @Inject
    public KvDao(Configuration cfg) {
        super(cfg);

        switch (cfg.dialect()) {
            case POSTGRES:
            case POSTGRES_9_5: {
                delegate = new PgKv();
                break;
            }
            case H2: {
                log.warn("init -> using H2-specific implementation, expect issues in multi server setups");
                delegate = new H2Kv();
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
        tx(tx -> delegate.put(tx, projectName, key, value));
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
        return txResult(tx -> delegate.inc(tx, projectName, key));
    }

    private interface Kv {

        void put(DSLContext tx, String projectName, String key, String value);

        long inc(DSLContext tx, String projectName, String key);
    }

    private static final class PgKv implements Kv {

        @Override
        public void put(DSLContext tx, String projectName, String key, String value) {
            int rows = tx.insertInto(PROJECT_KV_STORE)
                    .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_STRING)
                    .values(projectName, key, value)
                    .onConflict(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY)
                    .doUpdate().set(PROJECT_KV_STORE.VALUE_STRING, value)
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        }

        @Override
        public long inc(DSLContext tx, String projectName, String key) {
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
        }

        private long hash(String projectName, String key) {
            // should be "good enough" (tm) for advisory locking
            String s = projectName + "/" + key;
            long hash = 7;
            for (int i = 0; i < s.length(); i++) {
                hash = hash * 31 + s.charAt(i);
            }
            return hash;
        }
    }

    private static final class H2Kv implements Kv {

        @Override
        public void put(DSLContext tx, String projectName, String key, String value) {
            tx.mergeInto(PROJECT_KV_STORE)
                    .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_STRING)
                    .key(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY)
                    .values(projectName, key, value)
                    .execute();
        }

        @Override
        public synchronized long inc(DSLContext tx, String projectName, String key) {
            Long i = tx.select(PROJECT_KV_STORE.VALUE_LONG)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .forUpdate()
                    .fetchOne(PROJECT_KV_STORE.VALUE_LONG);

            if (i == null) {
                i = 1L;

                tx.insertInto(PROJECT_KV_STORE)
                        .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_LONG)
                        .values(projectName, key, i)
                        .execute();

                return i;
            }

            i += 1;

            int rows = tx.update(PROJECT_KV_STORE)
                    .set(PROJECT_KV_STORE.VALUE_LONG, i)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }

            return i;
        }
    }
}
