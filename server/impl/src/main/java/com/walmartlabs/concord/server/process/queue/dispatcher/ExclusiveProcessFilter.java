package com.walmartlabs.concord.server.process.queue.dispatcher;

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

import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

/**
 * Handles "exclusive" processes.
 * Exclusive processes can't be executed when there is another process
 * running in the same project.
 */
@Named
public class ExclusiveProcessFilter extends WaitProcessFinishFilter {

    private static final List<ProcessStatus> RUNNING_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.SUSPENDED,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    @Inject
    public ExclusiveProcessFilter(ProcessQueueDao processQueueDao) {
        super(processQueueDao);
    }

    @Override
    protected List<UUID> findProcess(DSLContext tx, ProcessQueueEntry item) {
        if (item.projectId() == null) {
            return Collections.emptyList();
        }

        ProcessQueue q = ProcessQueue.PROCESS_QUEUE.as("q");
        SelectConditionStep<Record1<UUID>> s = tx.select(q.INSTANCE_ID)
                .from(q)
                .where(q.PROJECT_ID.eq(item.projectId())
                        .and(q.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)));

        if (!item.exclusive()) {
            s.and(q.IS_EXCLUSIVE.eq(true));
        }

        // parent's
        if (item.parentInstanceId() != null) {
            SelectJoinStep<Record1<UUID>> parents = tx.withRecursive("parents").as(
                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.PARENT_INSTANCE_ID).from(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.INSTANCE_ID.eq(item.parentInstanceId()))
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

    @Override
    protected String getReason() {
        return "exclusive process";
    }
}
