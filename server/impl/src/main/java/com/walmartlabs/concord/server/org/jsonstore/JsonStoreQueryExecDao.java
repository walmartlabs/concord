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
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.select.*;
import org.jooq.Configuration;
import org.jooq.QueryPart;
import org.jooq.Record;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.INVENTORY_DATA;
import static com.walmartlabs.concord.server.jooq.Tables.JSON_STORE_DATA;
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
        String sql = createQuery(query, maxLimit);

        // TODO we should probably inspect the query to determine whether we need to bind the params or not

        QueryPart[] args;
        if (params == null) {
            args = new QueryPart[]{val(storeId)};
        } else {
            args = new QueryPart[]{val(objectMapper.toString(params)), val(storeId)};
        }

        return dsl().resultQuery(sql, args)
                .fetch(this::toExecResult);
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

    private static String createQuery(String src, Integer maxLimit) {
        try {
            Statement st = CCJSqlParserUtil.parse(src);
            st.accept(new StatementVisitorAdapter() {

                @Override
                public void visit(Select select) {
                    select.getSelectBody().accept(new SelectVisitorAdapter() {
                        @Override
                        public void visit(PlainSelect plainSelect) {
                            FromItem from = plainSelect.getFromItem();
                            if (from == null) {
                                return;
                            }

                            Alias fromAlias = from.getAlias();
                            Column left = null;

                            if (from instanceof Table) {
                                String tableName = ((Table) from).getName();
                                if (tableName.toLowerCase().contains("inventory_data")) {
                                    Table inventoryDataTable = new Table(INVENTORY_DATA.getName());
                                    inventoryDataTable.setAlias(fromAlias);
                                    left = new Column(inventoryDataTable, INVENTORY_DATA.INVENTORY_ID.getName());
                                }
                            }

                            if (left == null) {
                                // json_store.json_store_id
                                Table jsonStorageTable = new Table(JSON_STORE_DATA.getName());
                                jsonStorageTable.setAlias(fromAlias);
                                left = new Column(jsonStorageTable, JSON_STORE_DATA.JSON_STORE_ID.getName());
                            }

                            // cast(? as uuid)
                            CastExpression right = new CastExpression();
                            right.setLeftExpression(new JdbcParameter());
                            ColDataType t = new ColDataType();
                            t.setDataType("uuid");
                            right.setType(t);

                            // json_store_data.store_id = cast(? as uuid)
                            EqualsTo eq = new EqualsTo();
                            eq.setLeftExpression(left);
                            eq.setRightExpression(right);

                            Expression where = eq;
                            if (plainSelect.getWhere() != null) {
                                where = new AndExpression(plainSelect.getWhere(), eq);
                            }

                            if (maxLimit != null) {
                                long limitValue = maxLimit;
                                if (plainSelect.getLimit() != null) {
                                    Limit limit = plainSelect.getLimit();
                                    Expression rowCountExpr = limit.getRowCount();
                                    if (rowCountExpr instanceof LongValue) {
                                        LongValue v = (LongValue) rowCountExpr;
                                        if (v.getValue() < maxLimit) {
                                            limitValue = v.getValue();
                                        }
                                    }

                                }

                                Limit limit = new Limit();
                                limit.setRowCount(new LongValue(limitValue));
                                plainSelect.setLimit(limit);
                            }

                            plainSelect.setWhere(where);
                        }
                    });
                }
            });

            return st.toString();
        } catch (JSQLParserException e) {
            Throwable t = e;

            if (t.getCause() instanceof ParseException) {
                t = t.getCause();
            }

            throw new IllegalArgumentException("Query parse error: " + t.getMessage(), t);
        }
    }
}
