package com.walmartlabs.concord.server.process.queue.dispatcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.immutables.value.Value;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;

import javax.annotation.Nullable;
import java.util.*;

import static com.walmartlabs.concord.db.PgUtils.jsonbText;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

public class ExclusiveProcessFilterDao {

    private static final List<ProcessStatus> RUNNING_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.SUSPENDED,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    private final Map<CacheKey, List<UUID>> cache = new HashMap<>();

    public void cleanup() {
        cache.clear();
    }

    public List<UUID> findProcess(DSLContext tx, ProcessQueueEntry item, String group) {
        return cache.computeIfAbsent(CacheKey.of(group, item.projectId(), item.parentInstanceId()),
                key -> findProcess(tx, key.group(), key.projectId(), key.parentInstanceId()));
    }

    private List<UUID> findProcess(DSLContext tx, String group, UUID projectId, UUID parentInstanceId) {
        ProcessQueue q = ProcessQueue.PROCESS_QUEUE.as("q");
        SelectConditionStep<Record1<UUID>> s = tx.select(q.INSTANCE_ID)
                .from(q)
                .where(q.PROJECT_ID.eq(projectId)
                        .and(q.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)
                                .and(jsonbText(q.EXCLUSIVE, "group").eq(group))));

        // parent's
        if (parentInstanceId != null) {
            SelectJoinStep<Record1<UUID>> parents = tx.withRecursive("parents").as(
                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.PARENT_INSTANCE_ID).from(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                            .unionAll(
                                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.PARENT_INSTANCE_ID)
                                            .from(PROCESS_QUEUE)
                                            .join(name("parents"))
                                            .on(PROCESS_QUEUE.INSTANCE_ID.eq(
                                                    field(name("parents", "PARENT_INSTANCE_ID"), UUID.class)))))
                    .select(field("parents.INSTANCE_ID", UUID.class))
                    .from(name("parents"));

            s.and(q.INSTANCE_ID.notIn(parents));
        }

        return s.fetch(Record1::value1);
    }

    @Value.Immutable
    interface CacheKey {

        String group();

        UUID projectId();

        @Nullable
        UUID parentInstanceId();

        static CacheKey of(String group, UUID projectId, UUID parentInstanceId) {
            return ImmutableCacheKey.builder()
                    .group(group)
                    .projectId(projectId)
                    .parentInstanceId(parentInstanceId)
                    .build();
        }
    }
}
