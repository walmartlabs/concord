package com.walmartlabs.concord.server.org.inventory;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.InventoryDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.QueryPart;
import org.jooq.Record;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.InventoryData.INVENTORY_DATA;
import static org.jooq.impl.DSL.val;

@Named
public class InventoryQueryExecDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    private final InventoryQueryDao inventoryQueryDao;

    @Inject
    public InventoryQueryExecDao(@InventoryDB Configuration cfg,
                                 InventoryQueryDao inventoryQueryDao,
                                 ConcordObjectMapper objectMapper) {
        super(cfg);

        this.inventoryQueryDao = inventoryQueryDao;
        this.objectMapper = objectMapper;
    }

    public List<Object> exec(UUID queryId, Map<String, Object> params) {
        InventoryQueryEntry q = inventoryQueryDao.get(queryId);
        if (q == null) {
            return null;
        }

        String sql = createQuery(q.getText());

        try (DSLContext tx = DSL.using(cfg)) {
            // TODO we should probably inspect the query to determine whether we need to bind the params or not

            QueryPart[] args;
            if (params == null) {
                args = new QueryPart[]{val(q.getInventoryId())};
            } else {
                args = new QueryPart[]{val(objectMapper.toString(params)), val(q.getInventoryId())};
            }

            return tx.resultQuery(sql, args)
                    .fetch(this::toExecResult);
        }
    }

    private Object toExecResult(Record record) {
        return objectMapper.fromString((String) record.getValue(0));
    }

    private static String createQuery(String src) {
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

                            // inventory_data.inventory_id
                            Table inventoryDataTable = new Table(INVENTORY_DATA.getName());
                            inventoryDataTable.setAlias(fromAlias);
                            Column left = new Column(inventoryDataTable, INVENTORY_DATA.INVENTORY_ID.getName());

                            // cast(? as uuid)
                            CastExpression right = new CastExpression();
                            right.setLeftExpression(new JdbcParameter());
                            ColDataType t = new ColDataType();
                            t.setDataType("uuid");
                            right.setType(t);

                            // inventory_data.inventory_id = cast(? as uuid)
                            EqualsTo eq = new EqualsTo();
                            eq.setLeftExpression(left);
                            eq.setRightExpression(right);

                            Expression where = eq;
                            if (plainSelect.getWhere() != null) {
                                where = new AndExpression(plainSelect.getWhere(), eq);
                            }

                            plainSelect.setWhere(where);
                        }
                    });
                }
            });

            return st.toString();
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("Query parse error: " + e.getMessage(), e);
        }
    }
}
