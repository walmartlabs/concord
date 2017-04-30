package com.walmartlabs.concord.common.db;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.TransactionalRunnable;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Function;

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

    protected InputStream getData(Function<DSLContext, String> sqlFn, PreparedStatementHandler h, int columnIndex) {
        String sql;
        try (DSLContext create = DSL.using(cfg)) {
            sql = sqlFn.apply(create);
        }

        Connection conn = cfg.connectionProvider().acquire();
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            h.apply(ps);

            InputStream in = ResultSetInputStream.open(conn, ps, columnIndex);
            if (in == null) {
                JDBCUtils.safeClose(ps);
                JDBCUtils.safeClose(conn);
                return null;
            }
            return in;
        } catch (SQLException e) {
            JDBCUtils.safeClose(ps);
            JDBCUtils.safeClose(conn);
            throw new DataAccessException("Error while opening a stream", e);
        }
    }

    public void executeUpdate(DSLContext create, Function<DSLContext, String> sqlFn, PreparedStatementHandler h) {
        String sql = sqlFn.apply(create);
        create.connection(conn -> {
            try (PreparedStatement ps = conn.prepareCall(sql)) {
                h.apply(ps);
                ps.executeUpdate();
            }
        });
    }

    public interface Tx {

        void run(DSLContext tx) throws Exception;
    }

    public interface PreparedStatementHandler {

        void apply(PreparedStatement ps) throws SQLException;
    }
}
