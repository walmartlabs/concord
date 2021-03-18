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

import com.walmartlabs.concord.server.process.locks.LockEntry;
import com.walmartlabs.concord.server.process.locks.ProcessLocksDao;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Set;

/**
 * Handles the processes that are waiting for locks. Resumes a suspended process
 * if the lock was acquired successfully.
 */
@Named
@Singleton
public class WaitProcessLockHandler implements ProcessWaitHandler<ProcessLockCondition> {

    private static final Set<ProcessStatus> STATUSES = Collections.singleton(ProcessStatus.SUSPENDED);

    private final ProcessLocksDao locksDao;

    @Inject
    public WaitProcessLockHandler(ProcessLocksDao locksDao) {
        this.locksDao = locksDao;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_LOCK;
    }

    @Override
    public Set<ProcessStatus> getProcessStatuses() {
        return STATUSES;
    }

    @Override
    public Result<ProcessLockCondition> process(ProcessKey key, ProcessStatus status, ProcessLockCondition wait) {
        LockEntry lock = locksDao.tryLock(key.getInstanceId(), wait.orgId(), wait.projectId(), wait.scope(), wait.name());
        if (lock.instanceId().equals(key.getInstanceId())) {
            return Result.of(wait.name());
        }

        return Result.of(ProcessLockCondition.from(lock));
    }
}
