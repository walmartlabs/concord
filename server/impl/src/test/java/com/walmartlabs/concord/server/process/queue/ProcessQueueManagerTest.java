package com.walmartlabs.concord.server.process.queue;

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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class ProcessQueueManagerTest {

    @Test
    public void bulkStatusUpdateUsesCallerTransaction() {
        ProcessKey key = new ProcessKey(UUID.randomUUID(), OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));

        // the DAO must not run the update in a transaction of its own
        DSLContext ownDb = DSL.using(new MockConnection(ctx -> fail("query outside of the caller's transaction: " + ctx.sql())), SQLDialect.POSTGRES);
        ProcessQueueDao dao = new ProcessQueueDao(ownDb.configuration(), null);

        List<String> callerQueries = new ArrayList<>();
        DSLContext callerTx = DSL.using(new MockConnection(ctx -> {
            callerQueries.add(ctx.sql());
            var result = DSL.using(SQLDialect.POSTGRES).newResult(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT);
            result.add(DSL.using(SQLDialect.POSTGRES).newRecord(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT)
                    .values(key.getInstanceId(), key.getCreatedAt()));
            return new MockResult[]{new MockResult(1, result)};
        }), SQLDialect.POSTGRES);

        List<DSLContext> listenerTxs = new ArrayList<>();
        ProcessStatusListener listener = (tx, processKey, status) -> listenerTxs.add(tx);
        ProcessQueueManager manager = new ProcessQueueManager(dao, null, null, null, Set.of(listener));

        List<ProcessKey> updated = manager.updateExpectedStatus(callerTx, List.of(key), List.of(ProcessStatus.RUNNING), ProcessStatus.SUSPENDED);

        assertEquals(List.of(key), updated);
        assertEquals(1, callerQueries.size());
        assertEquals(1, listenerTxs.size());
        assertSame(callerTx, listenerTxs.get(0));
    }
}
