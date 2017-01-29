package com.walmartlabs.concord.bootstrap.db;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.TransactionalRunnable;
import org.jooq.impl.DSL;

public abstract class AbstractDao {

    protected final Configuration cfg;

    protected AbstractDao(Configuration cfg) {
        this.cfg = cfg;
    }

    protected void transaction(TransactionalRunnable r) {
        try (DSLContext create = DSL.using(cfg)) {
            create.transaction(r);
        }
    }
}
