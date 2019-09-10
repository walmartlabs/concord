package com.walmartlabs.concord.server.process.pipelines.processors;

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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadUtils;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ExclusiveGroupLock;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.jsonText;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

/**
 * If the process has an "exclusive group and mode cancel" assigned to it, this processor
 * determines whether the process can continue to run or should be cancelled.
 * The processor uses a global (DB) lock {@link ExclusiveGroupLock}.
 */
@Named
public class ExclusiveGroupProcessor implements PayloadProcessor {

    private static final String CANCEL_MODE = "cancel";

    private final LogManager logManager;
    private final ProcessQueueManager queueManager;
    private final ExclusiveProcessDao exclusiveProcessDao;
    private final ExclusiveGroupLock exclusiveGroupLock;

    @Inject
    public ExclusiveGroupProcessor(LogManager logManager,
                                   ProcessQueueManager queueManager,
                                   ExclusiveProcessDao exclusiveProcessDao,
                                   ExclusiveGroupLock exclusiveGroupLock) {

        this.logManager = logManager;
        this.queueManager = queueManager;
        this.exclusiveProcessDao = exclusiveProcessDao;
        this.exclusiveGroupLock = exclusiveGroupLock;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);

        Map<String, Object> exclusive = PayloadUtils.getExclusive(payload);
        if (exclusive.isEmpty()) {
            return chain.process(payload);
        }

        String mode = MapUtils.getString(exclusive, "mode", CANCEL_MODE);
        if (!CANCEL_MODE.equals(mode)) {
            return chain.process(payload);
        }

        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return chain.process(payload);
        }

        String group = MapUtils.getString(exclusive, "group");
        logManager.info(processKey, "Process' exclusive group: {}", group);

        boolean canContinue = exclusiveProcessDao.txResult(tx -> {
            exclusiveGroupLock.lock(tx);

            if (exclusiveProcessDao.exists(tx, processKey.getInstanceId(), parentInstanceId, projectId, group)) {
                logManager.warn(processKey, "Process(es) with exclusive group '" + group + "' is already in the queue. " +
                        "Current process has been cancelled");

                queueManager.updateStatus(processKey, ProcessStatus.CANCELLED);
                return false;
            }

            queueManager.updateExclusive(tx, processKey, exclusive);

            return true;
        });

        if (canContinue) {
            return chain.process(payload);
        }
        return payload;
    }

    @Named
    static class ExclusiveProcessDao extends AbstractDao  {

        private static final List<ProcessStatus> RUNNING_STATUSES = Arrays.asList(
                ProcessStatus.NEW,
                ProcessStatus.PREPARING,
                ProcessStatus.ENQUEUED,
                ProcessStatus.STARTING,
                ProcessStatus.RUNNING,
                ProcessStatus.SUSPENDED,
                ProcessStatus.RESUMING);

        @Inject
        protected ExclusiveProcessDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public boolean exists(DSLContext tx, UUID currentInstanceId, UUID parentInstanceId, UUID projectId, String exclusiveGroup) {
            SelectConditionStep<Record1<Integer>> s = tx.selectOne()
                            .from(PROCESS_QUEUE)
                            .where(jsonText(PROCESS_QUEUE.EXCLUSIVE, "group").eq(exclusiveGroup)
                                    .and(PROCESS_QUEUE.PROJECT_ID.eq(projectId)
                                            .and(PROCESS_QUEUE.INSTANCE_ID.notEqual(currentInstanceId)
                                                    .and(PROCESS_QUEUE.CURRENT_STATUS.in(RUNNING_STATUSES)))));

            // parent's
            if (parentInstanceId != null) {
                SelectJoinStep<Record1<UUID>> parents = tx.withRecursive("parents").as(
                        select(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID, ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID).from(ProcessQueue.PROCESS_QUEUE)
                                .where(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                                .unionAll(
                                        select(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID, ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID)
                                                .from(ProcessQueue.PROCESS_QUEUE)
                                                .join(name("parents"))
                                                .on(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID.eq(
                                                        field(name("parents", "PARENT_INSTANCE_ID"), UUID.class)))))
                        .select(field("parents.INSTANCE_ID", UUID.class))
                        .from(name("parents"));

                s.and(PROCESS_QUEUE.INSTANCE_ID.notIn(parents));
            }

            return tx.fetchExists(s);
        }
    }
}
