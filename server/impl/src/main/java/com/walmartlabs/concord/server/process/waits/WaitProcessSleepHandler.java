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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Date;

/**
 * Handles the processes that are waiting for some timeout. Resumes a suspended process
 * if the timeout exceeded.
 */
@Named
@Singleton
public class WaitProcessSleepHandler implements ProcessWaitHandler<ProcessSleepCondition> {

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_SLEEP;
    }

    @Override
    @WithTimer
    public Result<ProcessSleepCondition> process(ProcessKey key, ProcessSleepCondition wait) {
        if (wait.until().before(new Date())) {
            return Result.resume(wait.resumeEvent());
        }

        return Result.of(wait);
    }
}
