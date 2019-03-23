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

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.org.inventory.SqlParserTest.parseQueries;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore("local DB connection required")
public class InventoryQueryExecDaoTest extends AbstractDaoTest {

    @Test
    public void execQueryTest() throws Exception {
        List<String> queries = parseQueries("queries.txt");

        InventoryQueryDao qd = mock(InventoryQueryDao.class);
        InventoryQueryExecDao dao = new InventoryQueryExecDao(getConfiguration(), qd, new ConcordObjectMapper(TestObjectMapper.INSTANCE));

        for(String sql : queries) {
            UUID queryId = UUID.randomUUID();
            Map<String, Object> params = null;
            if (sql.contains("?::jsonb")) {
                params = new HashMap<>();
                params.put("k", "v");
            }
            when(qd.get(eq(queryId))).thenReturn(createInventoryQueryEntry(sql));

            List<Object> result = dao.exec(queryId, params);
            assertNotNull(result);
        }
    }

    private static InventoryQueryEntry createInventoryQueryEntry(String sql) {
        return new InventoryQueryEntry(UUID.randomUUID(), "test", UUID.randomUUID(), sql);
    }
}
