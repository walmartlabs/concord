package com.walmartlabs.concord.server.process.kv;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectKvStore.PROJECT_KV_STORE;

@Named
public class KvDao extends AbstractDao {

    @Inject
    public KvDao(Configuration cfg) {
        super(cfg);
    }

    public void remove(String projectName, String key) {
        tx(tx -> tx.deleteFrom(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                        .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                .execute());
    }

    public synchronized void put(String projectName, String key, String value) {
        tx(tx -> {
            Record1<String> r = tx.select(PROJECT_KV_STORE.VALUE_STRING)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .forUpdate()
                    .fetchOne();

            if (r == null) {
                tx.insertInto(PROJECT_KV_STORE)
                        .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_STRING)
                        .values(projectName, key, value)
                        .execute();
                return;
            }

            int rows = tx.update(PROJECT_KV_STORE)
                    .set(PROJECT_KV_STORE.VALUE_STRING, value)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + rows);
            }
        });
    }

    public String getString(String projectName, String key) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(PROJECT_KV_STORE.VALUE_STRING)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .fetchOne(PROJECT_KV_STORE.VALUE_STRING);
        }
    }

    public Long getLong(String projectName, String key) {
        try (DSLContext create = DSL.using(cfg)) {
            Record1<Long> r = create.select(PROJECT_KV_STORE.VALUE_LONG)
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

    // TODO #multiserver make it work in master-master configurations
    public synchronized long inc(String projectName, String key) {
        long initialValue = 1L;

        return txResult(cfg -> {
            DSLContext tx = DSL.using(cfg);

            Record1<Long> r = tx.select(PROJECT_KV_STORE.VALUE_LONG)
                    .from(PROJECT_KV_STORE)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .forUpdate()
                    .fetchOne();

            if (r == null) {
                tx.insertInto(PROJECT_KV_STORE)
                        .columns(PROJECT_KV_STORE.PROJECT_NAME, PROJECT_KV_STORE.VALUE_KEY, PROJECT_KV_STORE.VALUE_LONG)
                        .values(projectName, key, initialValue)
                        .execute();

                return initialValue;
            }

            long i = r.value1() + 1;

            int rows = tx.update(PROJECT_KV_STORE)
                    .set(PROJECT_KV_STORE.VALUE_LONG, i)
                    .where(PROJECT_KV_STORE.PROJECT_NAME.eq(projectName)
                            .and(PROJECT_KV_STORE.VALUE_KEY.eq(key)))
                    .execute();

            if (rows != 1) {
                throw new DataAccessException("Invalid number of rows: " + i);
            }

            return i;
        });
    }
}
