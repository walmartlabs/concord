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
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.QueryPart;
import org.jooq.Record;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.val;

/**
 * Executes JSON Store queries.
 * <p/>
 * Uses a separate DB connection pool {@link @JsonStorageDB} to allow
 * more fine-tuned security configuration.
 * @see #execSql(UUID, String, Map, Integer)
 */
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

    public List<Object> exec(UUID storeId, String queryName, Map<String, Object> params)
    {
        JsonStoreQueryEntry q = storeQueryDao.get(storeId, queryName);
        if (q == null) {
            throw new ValidationErrorsException("Query not found: " + queryName);
        }

        return execSql(q.storeId(), q.text(), params, null);
    }

    /**
     * Executes the provided query. The method replaces any usage of {@code JSON_STORE_DATA}
     * table with a restricted view {@code JSON_STORE_DATA_VIEW_RESTRICTED}. The view
     * requires the store ID to be passed as a session parameter.
     * <p/>
     * This is similar to how row-level security works in PostgreSQL, but doesn't exclude
     * the possibility of SQL injections. For production deployment it is recommended to
     * perform additional steps:
     * <ul>
     * <li>configure a separate user (see {@code db.inventoryUsername} in the Server's
     * configuration file)</li>
     * <li>install a RLS policy similar to <pre>{@code
     * CREATE POLICY json_store_data_restriction ON json_store_data
     * FOR SELECT
     * USING (json_store_id = current_setting('jsonStoreQueryExec.json_store_id'::text)::uuid);
     * }</pre></li>
     * <li>attach the policy to the new user {@code db.inventoryUsername}</li>
     * </ul>
     */
    public List<Object> execSql(UUID storeId, String query, Map<String, Object> params, Integer maxLimit)
    {
        String sql = query.replaceAll("(?i)json_store_data", "json_store_data_view_restricted");
        if (maxLimit != null) {
            sql = "select * from (" + trimEnd(sql, ';') + ") a limit " + maxLimit;
        }

        QueryPart[] args = params != null ? new QueryPart[] {val(objectMapper.toString(params))} : new QueryPart[0];

        String finalSql = sql;
        try {
            return dsl().transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                tx.execute("set local jsonStoreQueryExec.json_store_id='" + storeId + "'");
                return tx.resultQuery(finalSql, args)
                        .fetch(this::toExecResult);
            });
        } catch (Exception e) {
            String message = restoreOriginalQuery(e.getMessage());
            if (message == null) {
                throw e;
            }

            throw new RuntimeException(message, e.getCause());
        }
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

    private static String restoreOriginalQuery(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.replace("json_store_data_view_restricted", "json_store_data");
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
