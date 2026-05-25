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

import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class WaitProcessExternalEventHandler implements ProcessWaitHandler<ProcessExternalEventCondition> {

    private final ProcessLogManager logManager;

    @Inject
    public WaitProcessExternalEventHandler(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public WaitType getType() {
        return WaitType.EXTERNAL_EVENT;
    }

    @Override
    public List<Result<ProcessExternalEventCondition>> processBatch(
            List<WaitConditionItem<ProcessExternalEventCondition>> waits
    ) {

        // collect to grouping by process resume event. All waits for the same resume
        // event must be completed before we can process them together and resume.
        var byResumeEvent = waits.stream()
                .collect(groupingBy(wait -> wait.waitCondition().resumeEvent()));

        return byResumeEvent.values().stream()
                .flatMap(waitsForEvent -> {
                    var isAnyWaiting = waitsForEvent.stream()
                            .anyMatch(wait -> wait.waitCondition().waiting());

                    return isAnyWaiting
                            // still waiting on at least one condition to be cleared for the resume event
                            ? streamBatchNotReady(waitsForEvent)
                            // all wait conditions are satisfied, process may resume
                            : streamBatchReadyForResume(waitsForEvent);
                })
                .toList();
    }

    private Stream<Result<ProcessExternalEventCondition>> streamBatchNotReady(
            List<WaitConditionItem<ProcessExternalEventCondition>> waitsForEvent
    ) {
        return waitsForEvent.stream()
                .map(wait ->
                        Result.of(wait.processKey(), wait.waitConditionId(), wait.waitCondition()));
    }

    private Stream<Result<ProcessExternalEventCondition>> streamBatchReadyForResume(
            List<WaitConditionItem<ProcessExternalEventCondition>> waitsForEvent
    ) {

        return waitsForEvent.stream()
                .map(wait -> {
                    var resumeVars = saveVariablesAs(wait.processKey(), wait.waitCondition());
                    return Result.resume(wait, wait.waitCondition().resumeEvent(), resumeVars);
                }
        );
    }

    private Map<String, Serializable> saveVariablesAs(ProcessKey processKey, ProcessExternalEventCondition condition) {
        var saveAs = condition.saveAs();
        if (saveAs == null) {
            return Map.of();
        }

        var splits = saveAs.split("\\.");
        if (splits.length > 10) { // that's suspiciously deep
            logManager.warn(processKey, "External event condition 'saveAs' is too deep at {} levels. Resume variables will not be delivered.", splits.length);
            return Map.of();
        }

        return buildNestedMap(splits, 0, (Serializable) condition.variables());
    }

    /**
     * Recursively creates nested Maps from an array of keys
     * @param keys array of keys representing the nested structure
     * @param i current nested level in keys array
     * @param value Final value of deepest key in the nested Map structure
     */
    private static Map<String, Serializable> buildNestedMap(String[] keys, int i, Serializable value) {
        var map = new HashMap<String, Serializable>();
        if (i == keys.length - 1) {
            map.put(keys[i], value);
        } else {
            map.put(keys[i], (Serializable) buildNestedMap(keys, i + 1, value));
        }

        return map;
    }

}
