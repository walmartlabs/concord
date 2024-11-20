package com.walmartlabs.concord.plugins.mock;

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

import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.model.FlowCallOptions;
import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.vm.VMUtils;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.svm.VM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

public class MockTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(MockTask.class);

    private final Context ctx;
    private final String taskName;
    private final MockDefinitionProvider mockDefinitionProvider;
    private final Class<? extends Task> originalTaskClass;
    private final Supplier<Task> delegate;

    public MockTask(Context ctx, String taskName,
                    MockDefinitionProvider mockDefinitionProvider,
                    Class<? extends Task> originalTaskClass,
                    Supplier<Task> delegate) {
        this.ctx = ctx;
        this.taskName = taskName;
        this.mockDefinitionProvider = mockDefinitionProvider;
        this.originalTaskClass = originalTaskClass;
        this.delegate = delegate;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception{
        var mockDefinition = mockDefinitionProvider.find(ctx, taskName, input);
        if (mockDefinition == null) {
            return delegate.get().execute(input);
        }

        log.info("The actual task is not being executed; this is a mock");

        if (mockDefinition.throwError() != null) {
            throw new UserDefinedException(mockDefinition.throwError());
        }

        var result = mockDefinition.out();
        if (mockDefinition.executeFlow() != null) {
            var flowResult = executeFlow(mockDefinition.executeFlow(), input.toMap());
            result = assertMap(flowResult);
        }

        boolean success = MapUtils.getBoolean(result, "ok", true);
        return TaskResult.of(success)
                .values(result);
    }

    public Object call(CustomTaskMethodResolver.InvocationContext ic, String method, Class<?>[] paramTypes, Object[] params) {
        var mockDefinition = mockDefinitionProvider.find(ctx, taskName, method, params);
        if (mockDefinition == null) {
            return ic.invoker().invoke(delegate.get(), method, paramTypes, params);
        }

        log.info("The actual '{}.{}()' is not being executed; this is a mock", taskName, method);

        if (mockDefinition.throwError() != null) {
            throw new UserDefinedException(mockDefinition.throwError());
        }

        var result = mockDefinition.result();
        if (mockDefinition.executeFlow() != null) {
            result = executeFlow(mockDefinition.executeFlow(), toMap(params));
        }

        return result;
    }

    public String taskName() {
        return taskName;
    }

    public Class<? extends Task> originalTaskClass() {
        return originalTaskClass;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Serializable executeFlow(String flowName, Map<String, Object> input) {
        log.info("Executing flow '{}' to get mock results", flowName);

        var runtime = ctx.execution().runtime();
        var state = ctx.execution().state();
        var compiler = runtime.getService(Compiler.class);
        var pd = runtime.getService(ProcessDefinition.class);

        var callOptions = FlowCallOptions.builder()
                .input((Map)input)
                .addOut("result")
                .build();
        var flowCallCommand = compiler.compile(pd, new FlowCall(Location.builder().build(), flowName, callOptions));

        var currentThreadId = ctx.execution().currentThreadId();
        var forkThreadId = state.nextThreadId();

        state.fork(currentThreadId, forkThreadId, flowCallCommand);

        var targetFrame = state.peekFrame(forkThreadId);
        VMUtils.putLocals(targetFrame, VMUtils.getCombinedLocals(state, currentThreadId));

        try {
            var result = runtime.eval(state, forkThreadId);
            return result.lastFrame().getLocal("result");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked"})
    private Map<String, Object> assertMap(Serializable maybeMap) {
        if (maybeMap == null) {
            return Map.of();
        }

        if (maybeMap instanceof Map<?,?>) {
            return (Map<String, Object>) maybeMap;
        }

        throw new IllegalArgumentException("Flow should set result as Map. Actual: " + maybeMap.getClass());
    }

    private static Map<String, Object> toMap(Object[] params) {
        if (params == null || params.length == 0) {
            return Map.of();
        }

        return Map.of("args", Arrays.asList(params));
    }
}
