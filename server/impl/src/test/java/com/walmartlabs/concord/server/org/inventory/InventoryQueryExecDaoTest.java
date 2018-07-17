package com.walmartlabs.concord.server.org.inventory;

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

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
        InventoryQueryExecDao dao = new InventoryQueryExecDao(getConfiguration(), qd);

        for(String sql : queries) {
            UUID queryId = UUID.randomUUID();
            Map<String, Object> params = new HashMap<>();
            if (sql.contains("?::jsonb")) {
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
