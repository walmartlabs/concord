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
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ExclusiveGroupLock;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_QUEUE;

/**
 * If the process has an "exclusive group" assigned to it, this processor
 * determines whether the process can continue to run or should be cancelled.
 * The processor uses a global (DB) lock {@link ExclusiveGroupLock}.
 */
@Named
public class ExclusiveGroupProcessor implements PayloadProcessor {

    private final LogManager logManager;
    private final ProcessQueueManager queueManager;
    private final ProcessQueueDao queueDao;
    private final ExclusiveProcessDao exclusiveProcessDao;
    private final ExclusiveGroupLock exclusiveGroupLock;

    @Inject
    public ExclusiveGroupProcessor(LogManager logManager,
                                   ProcessQueueManager queueManager,
                                   ProcessQueueDao queueDao,
                                   ExclusiveProcessDao exclusiveProcessDao,
                                   ExclusiveGroupLock exclusiveGroupLock) {

        this.logManager = logManager;
        this.queueManager = queueManager;
        this.queueDao = queueDao;
        this.exclusiveProcessDao = exclusiveProcessDao;
        this.exclusiveGroupLock = exclusiveGroupLock;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        String exclusiveGroup = payload.getHeader(Payload.EXCLUSIVE_GROUP);
        if (exclusiveGroup == null) {
            return chain.process(payload);
        }

        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Process' exclusive group: {}", exclusiveGroup);

        boolean canContinue = exclusiveProcessDao.txResult(tx -> {
            exclusiveGroupLock.lock(tx);

            if (exclusiveProcessDao.exists(tx, processKey.getInstanceId(), projectId, exclusiveGroup)) {
                logManager.warn(processKey, "Process(es) with exclusive group '" + exclusiveGroup + "' is already in the queue. " +
                        "Current process has been cancelled");

                queueManager.updateStatus(processKey, ProcessStatus.CANCELLED);
                return false;
            }

            return true;
        });

        if (canContinue) {
            return chain.process(payload);
        }
        return payload;
    }

    @Named
    static class ExclusiveProcessDao extends AbstractDao  {

        private static final List<ProcessStatus> FINISHED_STATUSES = Arrays.asList(
                ProcessStatus.FINISHED,
                ProcessStatus.FAILED,
                ProcessStatus.CANCELLED,
                ProcessStatus.TIMED_OUT);

        @Inject
        protected ExclusiveProcessDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public boolean exists(DSLContext tx, UUID currentInstanceId, UUID projectId, String exclusiveGroup) {
            return tx.fetchExists(
                    tx.selectOne()
                            .from(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.EXCLUSIVE_GROUP.eq(exclusiveGroup)
                                    .and(PROCESS_QUEUE.PROJECT_ID.eq(projectId)
                                            .and(PROCESS_QUEUE.INSTANCE_ID.notEqual(currentInstanceId)
                                                    .and(PROCESS_QUEUE.CURRENT_STATUS.notIn(FINISHED_STATUSES))))));
        }
    }
}
