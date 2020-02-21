package com.walmartlabs.concord.server.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class DbUtils {

    /**
     * Do not use it as generic "INSERT ... ON CONFLICT UPDATE" solution.
     * It works only until there's only one "inserter".
     */
    public static <E> void upsert(DSLContext tx, List<E> items,
                                  Update<E> update, Insert<E> insert) {

        if (items.isEmpty()) {
            return;
        }

        tx.connection(conn -> {
            int[] updated = update.call(tx, conn, items);
            List<E> forInsert = new ArrayList<>();
            for (int i = 0; i < updated.length; i++) {
                if (updated[i] < 1) {
                    forInsert.add(items.get(i));
                }
            }
            if (!forInsert.isEmpty()) {
                insert.call(tx, conn, forInsert);
            }
        });
    }

    @FunctionalInterface
    public interface Update<E> {

        int[] call(DSLContext tx, Connection conn, List<E> items) throws SQLException;
    }

    @FunctionalInterface
    public interface Insert<E> {

        void call(DSLContext tx, Connection conn, List<E> items) throws SQLException;
    }

    private DbUtils() {
    }
}
