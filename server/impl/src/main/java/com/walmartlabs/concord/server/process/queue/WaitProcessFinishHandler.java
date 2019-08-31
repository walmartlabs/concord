package com.walmartlabs.concord.server.process.queue;

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

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.process.queue.ProcessCompletionCondition.CompleteCondition;

/**
 * Handles the processes that are waiting for other processes to finish.
 */
@Named
@Singleton
public class WaitProcessFinishHandler implements ProcessWaitHandler<ProcessCompletionCondition> {

    private final Set<ProcessStatus> STATUSES = new HashSet<>(Arrays.asList(ProcessStatus.ENQUEUED, ProcessStatus.SUSPENDED));

    // TODO: remove me when all conditions migrated to new format
    private static final Set<ProcessStatus> DEFAULT_FINISHED_STATUSES = ImmutableSet.of(
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.TIMED_OUT);

    private final Dao dao;
    private final ProcessManager processManager;
    private final PayloadManager payloadManager;

    @Inject
    public WaitProcessFinishHandler(Dao dao, ProcessManager processManager, PayloadManager payloadManager) {
        this.dao = dao;
        this.processManager = processManager;
        this.payloadManager = payloadManager;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_COMPLETION;
    }

    @Override
    public Set<ProcessStatus> getProcessStatuses() {
        return STATUSES;
    }

    @Override
    public ProcessCompletionCondition process(UUID instanceId, ProcessStatus processStatus, ProcessCompletionCondition wait) {
        Set<ProcessStatus> finishedStatuses = DEFAULT_FINISHED_STATUSES;

        Set<ProcessStatus> finalStatuses = wait.finalStatuses();
        if (finalStatuses != null && !finalStatuses.isEmpty()) {
            finishedStatuses = finalStatuses;
        }

        Set<UUID> awaitProcesses = wait.processes();

        Set<UUID> finishedProcesses = dao.findFinished(awaitProcesses, finishedStatuses);
        if (finishedProcesses.isEmpty()) {
            return wait;
        }

        boolean completed = isCompleted(wait.completeCondition(), awaitProcesses, finishedProcesses);
        if (completed) {
            if (wait.resumeEvent() != null) {
                resumeProcess(instanceId, wait.resumeEvent());
            }
            return null;
        }

        List<UUID> processes = new ArrayList<>(awaitProcesses);
        processes.removeAll(finishedProcesses);

        return ProcessCompletionCondition.builder().from(wait)
                .processes(processes)
                .reason(wait.reason())
                .build();
    }

    private void resumeProcess(UUID instanceId, String eventName) {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(PartialProcessKey.from(instanceId), eventName, null);
        } catch (IOException e) {
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        processManager.resume(payload);
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
