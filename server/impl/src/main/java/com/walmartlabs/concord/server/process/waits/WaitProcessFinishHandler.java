package com.walmartlabs.concord.server.process.waits;

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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the processes that are waiting for other processes to finish.
 */
@Singleton
public class WaitProcessFinishHandler implements ProcessWaitHandler<ProcessCompletionCondition> {

    private final ProcessQueueManager processQueueManager;

    @Inject
    public WaitProcessFinishHandler(ProcessQueueManager processQueueManager) {
        this.processQueueManager = processQueueManager;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_COMPLETION;
    }

    @Override
    @WithTimer
    public Result<ProcessCompletionCondition> process(ProcessKey processKey, ProcessCompletionCondition wait) {
        Set<UUID> awaitProcesses = wait.processes();
        if (!awaitProcesses.isEmpty()) {
            return Result.of(wait);
        }

        if (wait.resumeEvent() != null) {
            return Result.resume(wait.resumeEvent());
        } else {
            return Result.action(tx -> processQueueManager.updateExpectedStatus(tx, processKey, ProcessStatus.WAITING, ProcessStatus.ENQUEUED));
        }
    }
}
