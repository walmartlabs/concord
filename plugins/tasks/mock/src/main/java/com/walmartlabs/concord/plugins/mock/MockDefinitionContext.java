package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Objects;

public record MockDefinitionContext(Step currentStep, String taskName, Variables input, String method, Object[] params) {

    public static MockDefinitionContext task(Step currentStep, String taskName, Variables input) {
        Objects.requireNonNull(taskName);
        Objects.requireNonNull(input);

        return new MockDefinitionContext(currentStep, taskName, input, null, null);
    }

    public static MockDefinitionContext method(Step currentStep, String taskName, String methodName, Object[] params) {
        Objects.requireNonNull(taskName);
        Objects.requireNonNull(methodName);
        Objects.requireNonNull(params);
        return new MockDefinitionContext(currentStep, taskName, null, methodName, params);
    }
}
