package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager.StateRow;
import com.walmartlabs.concord.server.process.state.ProcessStateManager.StateTable;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Converts persisted process identities before background tasks and HTTP start. */
public class SecurityStateMigrator {
    private static final long LOCK_KEY = 0x436f6e636f726453L;
    private static final int BATCH_SIZE = 100;

    private final Configuration configuration;
    private final ProcessStateManager stateManager;

    @Inject
    public SecurityStateMigrator(@MainDB Configuration configuration, ProcessStateManager stateManager) {
        this.configuration = configuration;
        this.stateManager = stateManager;
    }

    public void migrate() {
        Connection connection = configuration.connectionProvider().acquire();
        try {
            boolean autoCommit = connection.getAutoCommit();
            try {
                // The main pool defaults to auto-commit off. Starting from
                // auto-commit ensures each jOOQ transaction commits its row,
                // rather than creating a savepoint in one long transaction.
                connection.setAutoCommit(true);
                DSLContext db = DSL.using(connection, configuration.dialect());
                db.execute("select pg_advisory_lock(?)", LOCK_KEY);
                try {
                    requireTimestampWithTimeZone(db, "process_initial_state");
                    requireTimestampWithTimeZone(db, "process_state");
                    migrate(db, StateTable.INITIAL, "initiator");
                    migrate(db, StateTable.CURRENT, ".concord/current_user");
                } finally {
                    db.execute("select pg_advisory_unlock(?)", LOCK_KEY);
                }
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to configure security state migration connection", e);
        } finally {
            configuration.connectionProvider().release(connection);
        }
    }

    private static void requireTimestampWithTimeZone(DSLContext db, String table) {
        Object valid = db.fetchValue("select atttypid = 'timestamptz'::regtype from pg_attribute " +
                "where attrelid = cast(? as regclass) and attname = 'instance_created_at' and not attisdropped",
                table);
        if (!Boolean.TRUE.equals(valid)) {
            throw new IllegalStateException(table + ".instance_created_at must be timestamp with time zone; " +
                    "repair the database timezone migration before converting execution identities");
        }
    }

    private void migrate(DSLContext db, StateTable table, String path) {
        StateRow cursor = null;
        while (true) {
            List<StateRow> rows = stateManager.enumerateState(db, table, path, cursor, BATCH_SIZE);
            if (rows.isEmpty()) {
                return;
            }
            for (StateRow row : rows) {
                try {
                    db.transaction(cfg -> stateManager.updateStateData(DSL.using(cfg), table, row, bytes -> {
                        var identity = ExecutionIdentityCodec.decode(bytes);
                        return ExecutionIdentityCodec.isLegacy(bytes) ? ExecutionIdentityCodec.encode(identity) : null;
                    }));
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Unable to migrate " + table + " state row " + row, e);
                }
            }
            cursor = rows.get(rows.size() - 1);
        }
    }
}
