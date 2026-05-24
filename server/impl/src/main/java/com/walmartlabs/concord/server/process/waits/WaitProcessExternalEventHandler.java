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
import java.util.stream.Collectors;

public class WaitProcessExternalEventHandler implements ProcessWaitHandler<ProcessExternalEventCondition> {

    @Override
    public WaitType getType() {
        return WaitType.EXTERNAL_EVENT;
    }

    @Override
    public List<Result<ProcessExternalEventCondition>> processBatch(List<WaitConditionItem<ProcessExternalEventCondition>> waits) {

        var byResumeEvent = waits.stream().collect(Collectors.groupingBy(wait -> wait.waitCondition().resumeEvent()));

        // TODO break this into multiple methods to make it readable
        return byResumeEvent.entrySet().stream()
                .flatMap(e -> {
                    var waitsForEvent = e.getValue();

                    var allWaitsComplete = waitsForEvent.stream().noneMatch(wait -> wait.waitCondition().waiting());
                    if (allWaitsComplete) {
                        return waitsForEvent.stream().map(wait ->
                                Result.<ProcessExternalEventCondition>resume(wait, wait.waitCondition().resumeEvent(), wait.waitCondition().variables())
                        );
                    } else {
                        // still waiting on wat least one condition to be cleared for the resume event
                        return waitsForEvent.stream().map(wait ->
                                Result.of(wait.processKey(), wait.waitConditionId(), wait.waitCondition())
                        );
                    }
                })
                .toList();


//        // For each wait, if waiting == false, resume the process and pass variables
//        return waits.stream().map(wait -> {
//            ProcessExternalEventCondition cond = wait.waitCondition();
//            if (!cond.waiting()) {
//                // Resume process, pass eventKey as resumeEvent
//                return Result.<ProcessExternalEventCondition>resume(wait, cond.resumeEvent(), cond.variables());
//            }
//
//            // Still waiting
//            return Result.of(wait.processKey(), wait.waitConditionId(), cond);
//        }).toList();
    }
}

