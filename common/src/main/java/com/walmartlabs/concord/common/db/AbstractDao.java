package com.walmartlabs.concord.common.db;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.TransactionalRunnable;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    protected void tx(Tx t) {
        try (DSLContext ctx = DSL.using(cfg)) {
            ctx.transaction(cfg -> {
                DSLContext tx = DSL.using(cfg);
                t.run(tx);
            });
        }
    }

    protected <T, R> List<R> mapToList(Collection<T> elements, Function<T, R> f) {
        if (elements == null) {
            return null;
        }
        return elements.stream().map(f).collect(Collectors.toList());
    }

    protected <T> void forEach(Collection<T> elements, Consumer<T> consumer) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        elements.stream().forEach(consumer);
    }

    public interface Tx {

        void run(DSLContext tx) throws Exception;
    }
}
