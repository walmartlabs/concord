package com.walmartlabs.concord.server.process.waits;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.util.List;

public class WaitProcessExternalEventHandler implements ProcessWaitHandler<ProcessExternalEventCondition> {

    @Override
    public WaitType getType() {
        return WaitType.EXTERNAL_EVENT;
    }

    @Override
    public List<Result<ProcessExternalEventCondition>> processBatch(List<WaitConditionItem<ProcessExternalEventCondition>> waits) {
        // For each wait, if waiting == false, resume the process and pass variables
        return waits.stream().map(wait -> {
            ProcessExternalEventCondition cond = wait.waitCondition();
            if (!cond.waiting()) {
                // Resume process, pass eventKey as resumeEvent
                return Result.<ProcessExternalEventCondition>resume(wait, cond.resumeEvent(), cond.variables());
            }

            // Still waiting
            return Result.of(wait.processKey(), wait.waitConditionId(), cond);
        }).toList();
    }
}

