package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.google.inject.Inject;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.DefaultTaskVariablesService;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallEvent;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;

import java.util.List;

public class InvocationsCollector implements TaskCallListener, ExecutionListener {

    private final InvocationsCollectorParams params;
    private final Invocations invocations;

    @Inject
    public InvocationsCollector(DefaultTaskVariablesService defaultTaskVariablesService,
                                Invocations invocations) {

        this.params = new InvocationsCollectorParams(defaultTaskVariablesService.get("invocations"));
        this.invocations = invocations;
    }

    @Override
    public void onEvent(TaskCallEvent event) {
        if (!params.enabled() || event.phase() != TaskCallEvent.Phase.PRE) {
            return;
        }

        Step currentStep = event.currentStep();

        invocations.record(Invocation.builder()
                .fileName(currentStep.getLocation().fileName())
                .line(currentStep.getLocation().lineNum())
                .taskName(event.taskName())
                .methodName(event.methodName())
                .args(sanitizeInput(event.methodName(), event.input()))
                .build());
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        if (!params.enabled()) {
            return;
        }

        if (!isSuspended(state)) {
            invocations.cleanup();
        }
    }

    @Override
    public void onProcessError(Runtime runtime, State state, Exception e) {
        if (!params.enabled()) {
            return;
        }

        invocations.cleanup();
    }

    private static List<Object> sanitizeInput(String methodName, List<Object> input) {
        // task call without input -> omit variables so we can use
        // ${verify.task('testTask', 1).execute()}
        if ("execute".equals(methodName) && input.size() == 1 && input.get(0) instanceof Variables variables) {
            var m = variables.toMap();
            if (m.isEmpty()) {
                return List.of();
            }
        }

        return InputSanitizer.sanitize(input);
    }

    private static boolean isSuspended(State state) {
        return state.threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }
}
