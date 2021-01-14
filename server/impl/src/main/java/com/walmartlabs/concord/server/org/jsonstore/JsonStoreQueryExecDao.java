package com.walmartlabs.concord.server.org.jsonstore;

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

import com.fasterxml.jackson.core.JsonParseException;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.JsonStorageDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.QueryPart;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.val;

@Named
public class JsonStoreQueryExecDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;
    private final JsonStoreQueryDao storeQueryDao;

    @Inject
    public JsonStoreQueryExecDao(@JsonStorageDB Configuration cfg,
                                 ConcordObjectMapper objectMapper,
                                 JsonStoreQueryDao storeQueryDao) {

        super(cfg);
        this.objectMapper = objectMapper;
        this.storeQueryDao = storeQueryDao;
    }

    public List<Object> exec(UUID storeId, String queryName, Map<String, Object> params) {
        JsonStoreQueryEntry q = storeQueryDao.get(storeId, queryName);
        if (q == null) {
            throw new ValidationErrorsException("Query not found: " + queryName);
        }

        return execSql(q.storeId(), q.text(), params, null);
    }

    public List<Object> execSql(UUID storeId, String query, Map<String, Object> params, Integer maxLimit) {
        String sql = query.replace("json_store_data", "json_store_data_view_restricted");
        if (maxLimit != null) {
            sql = "select * from (" + trimEnd(sql, ';') + ") a limit " + maxLimit;
        }

        QueryPart[] args = params != null ? new QueryPart[]{val(objectMapper.toString(params))} : new QueryPart[0];

        String resultSql = sql;
        return dsl().transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);
            tx.execute("set local jsonStoreQueryExec.json_store_id='" + storeId + "'");
            return tx.resultQuery(resultSql, args)
                    .fetch(this::toExecResult);
        });
    }

    private Object toExecResult(Record record) {
        Object value = record.get(0);
        if (value == null) {
            return null;
        }

        if (record.size() > 1) {
            throw new ValidationErrorsException("Invalid query result type: expected a single column, got " + record.size() + " columns. " +
                    "Change the query to return a single column or to build a JSON object.");
        }

        try {
            return objectMapper.fromString(value.toString(), Object.class);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof JsonParseException) {
                throw new RuntimeException("Invalid JSON value: " + value + ". Expected a valid JSON object.");
            }

            throw e;
        }
    }

    private static String trimEnd(String value, char c) {
        int len = value.length();
        int i = 0;
        while ((i < len) && (value.charAt(len - 1) == c || Character.isWhitespace(value.charAt(len - 1)))) {
            len--;
        }
        return value.substring(0, len);
    }
}
