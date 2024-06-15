package com.walmartlabs.concord.server.org.jsonstore;

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

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled("local DB connection required")
public class JsonStorageQueryExecDaoTest extends AbstractDaoTest {

    @Test
    public void execQueryTest() throws Exception {
        List<String> queries = parseQueries("queries.txt");

        JsonStoreQueryDao qd = mock(JsonStoreQueryDao.class);
        JsonStoreQueryExecDao dao = new JsonStoreQueryExecDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE), qd);

        UUID storageId = UUID.randomUUID();
        for(String sql : queries) {
            String queryName = "test";
            UUID queryId = UUID.randomUUID();
            Map<String, Object> params = null;
            if (sql.contains("?::jsonb")) {
                params = new HashMap<>();
                params.put("k", "v");
            }
            when(qd.get(eq(storageId), eq(queryName))).thenReturn(JsonStoreQueryEntry.builder()
                    .id(queryId)
                    .storeId(storageId)
                    .name(queryName)
                    .text(sql)
                    .build());

            List<Object> result = dao.exec(storageId, queryName, params);
            assertNotNull(result);
        }
    }

    private static List<String> parseQueries(String resourceName) throws IOException {
        List<String> queries = new ArrayList<>();

        try (InputStream in = JsonStorageQueryExecDaoTest.class.getResourceAsStream(resourceName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            int brk = 0;

            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    brk++;
                    continue;
                }

                if (brk >= 2) {
                    queries.add(sb.toString());
                    sb = new StringBuilder();
                }

                sb.append(line).append(" ");
                brk = 0;
            }
        }
        return queries;
    }
}
