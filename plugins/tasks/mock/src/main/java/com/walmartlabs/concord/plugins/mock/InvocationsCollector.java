package com.walmartlabs.concord.plugins.mock;

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

    private static List<Object> sanitizeInput(String methodName, List<Object> input) {
        // task call
        if ("execute".equals(methodName) && input.size() == 1 && input.get(0) instanceof Variables variables) {
            var m = variables.toMap();
            if (m.isEmpty()) {
                return List.of();
            }
            return List.of(m);
        }

        return InputSanitizer.sanitize(input);
    }

    private static boolean isSuspended(State state) {
        return state.threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }
}
