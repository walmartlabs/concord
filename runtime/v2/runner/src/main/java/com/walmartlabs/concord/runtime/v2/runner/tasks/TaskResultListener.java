package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.runtime.v2.runner.TaskResultService;

import javax.inject.Inject;
import java.util.Set;

public class TaskResultListener implements TaskCallListener {

    private final Set<String> tasksForCollect;
    private final TaskResultService taskResults;

    @Inject
    public TaskResultListener(PolicyEngine policyEngine, TaskResultService taskResults) {
        this.tasksForCollect = policyEngine.getTaskPolicy().getTaskResults();
        this.taskResults = taskResults;
    }

    @Override
    public void onEvent(TaskCallEvent event) {
        if (event.phase() == TaskCallEvent.Phase.PRE || event.result() == null) {
            return;
        }

        if (!tasksForCollect.contains(event.taskName())) {
            return;
        }

        taskResults.store(event.taskName(), event.result());
    }
}
