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

import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.locks.LockEntry;
import com.walmartlabs.concord.server.process.locks.ProcessLocksDao;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.UUID;

/**
 * Handles the processes that are waiting for locks. Resumes a suspended process
 * if the lock was acquired successfully.
 */
@Named
@Singleton
public class WaitProcessLockHandler implements ProcessWaitHandler<ProcessLockCondition> {

    private final ProcessLocksDao locksDao;
    private final ProcessManager processManager;
    private final PayloadManager payloadManager;

    @Inject
    public WaitProcessLockHandler(ProcessLocksDao locksDao, ProcessManager processManager, PayloadManager payloadManager) {
        this.locksDao = locksDao;
        this.processManager = processManager;
        this.payloadManager = payloadManager;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_LOCK;
    }

    @Override
    public ProcessLockCondition process(UUID instanceId, ProcessStatus status, ProcessLockCondition wait) {
        if (status != ProcessStatus.SUSPENDED) {
            return wait;
        }

        LockEntry lock = locksDao.tryLock(instanceId, wait.orgId(), wait.projectId(), wait.scope(), wait.name());
        if (lock.instanceId().equals(instanceId)) {
            resumeProcess(instanceId, wait.name());
            return null;
        }

        return ProcessLockCondition.from(lock);
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
}
