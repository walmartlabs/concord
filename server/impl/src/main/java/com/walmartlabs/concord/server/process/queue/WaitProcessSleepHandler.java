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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * Handles the processes that are waiting for some timeout. Resumes a suspended process
 * if the timeout exceeded.
 */
@Named
@Singleton
public class WaitProcessSleepHandler implements ProcessWaitHandler<ProcessSleepCondition> {

    private static final Set<ProcessStatus> STATUSES = Collections.singleton(ProcessStatus.SUSPENDED);

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_SLEEP;
    }

    @Override
    public Set<ProcessStatus> getProcessStatuses() {
        return STATUSES;
    }

    @Override
    public Result<ProcessSleepCondition> process(ProcessKey key, ProcessStatus status, ProcessSleepCondition wait) {
        if (wait.until().before(new Date())) {
            return Result.of(wait.resumeEvent());
        }

        return Result.of(wait);
    }
}
