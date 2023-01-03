package com.walmartlabs.concord.server.process.waits;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.process.waits.ProcessCompletionCondition.CompleteCondition;

/**
 * Handles the processes that are waiting for other processes to finish.
 */
@Named
@Singleton
public class WaitProcessFinishHandler implements ProcessWaitHandler<ProcessCompletionCondition> {

    private final Dao dao;
    private final ProcessQueueManager processQueueManager;

    @Inject
    public WaitProcessFinishHandler(Dao dao, ProcessQueueManager processQueueManager) {
        this.dao = dao;
        this.processQueueManager = processQueueManager;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_COMPLETION;
    }

    @Override
    @WithTimer
    public Result<ProcessCompletionCondition> process(ProcessKey processKey, ProcessCompletionCondition wait) {
        Set<ProcessStatus> finishedStatuses = wait.finalStatuses();
        Set<UUID> awaitProcesses = wait.processes();

        Set<UUID> finishedProcesses = dao.findFinished(awaitProcesses, finishedStatuses);
        if (finishedProcesses.isEmpty()) {
            return Result.of(wait);
        }

        boolean completed = isCompleted(wait.completeCondition(), awaitProcesses, finishedProcesses);
        if (completed) {
            if (wait.resumeEvent() != null) {
                return Result.resume(wait.resumeEvent());
            } else {
                return Result.action(tx -> processQueueManager.updateExpectedStatus(tx, processKey, ProcessStatus.WAITING, ProcessStatus.ENQUEUED));
            }
        }

        List<UUID> processes = new ArrayList<>(awaitProcesses);
        processes.removeAll(finishedProcesses);

        return Result.of(
                ProcessCompletionCondition.builder().from(wait)
                        .processes(processes)
                        .reason(wait.reason())
                        .build());
    }

    private static boolean isCompleted(CompleteCondition condition, Set<UUID> awaitProcesses, Set<UUID> finishedProcesses) {
        switch (condition) {
            case ALL: {
                return awaitProcesses.size() == finishedProcesses.size();
            }
            case ONE_OF: {
                return !finishedProcesses.isEmpty();
            }
            default:
                throw new IllegalArgumentException("Unknown condition type: " + condition);
        }
    }

    @Named
    private static final class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public Set<UUID> findFinished(Set<UUID> awaitProcesses, Set<ProcessStatus> finishedStatuses) {
            return txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");
                return tx.select(q.INSTANCE_ID)
                        .from(q)
                        .where(q.INSTANCE_ID.in(awaitProcesses)
                                .and(q.CURRENT_STATUS.in(finishedStatuses)))
                        .fetchSet(q.INSTANCE_ID);
            });
        }
    }
}
