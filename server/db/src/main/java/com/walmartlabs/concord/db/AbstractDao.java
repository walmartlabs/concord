package com.walmartlabs.concord.db;

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

import org.jooq.Configuration;
import org.jooq.DSLContext;
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

    protected DSLContext dsl() {
        return DSL.using(cfg);
    }

    protected void tx(Tx t) {
        dsl().transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            t.run(tx);
        });
    }

    protected <T> T txResult(TxResult<T> t) {
        return dsl().transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);
            return t.run(tx);
        });
    }

    protected InputStream getData(Function<DSLContext, String> sqlFn, PreparedStatementHandler h, int columnIndex) {
        String sql = sqlFn.apply(dsl());

        Connection conn = cfg.connectionProvider().acquire(); // NOSONAR
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            h.apply(ps);

            InputStream in = ResultSetInputStream.open(conn, ps, columnIndex);
            if (in == null) { // NOSONAR
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

    public interface Tx {

        void run(DSLContext tx) throws Exception;
    }

    public interface TxResult<T> {

        T run(DSLContext tx) throws Exception;
    }

    public interface PreparedStatementHandler {

        void apply(PreparedStatement ps) throws SQLException;
    }
}
