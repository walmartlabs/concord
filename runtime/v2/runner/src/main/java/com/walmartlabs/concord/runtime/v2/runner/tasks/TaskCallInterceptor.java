package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.common.AllowNulls;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallEvent.Phase;
import org.immutables.value.Value;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Intercepts task calls and notifies {@link TaskCallListener}s.
 */
public class TaskCallInterceptor {

    private final Set<TaskCallListener> listeners;

    @Inject
    public TaskCallInterceptor(Set<TaskCallListener> listeners) {
        this.listeners = listeners;
    }

    public <T> T invoke(CallContext ctx, Method method, Callable<T> callable) throws Exception {
        // record the PRE event
        TaskCallEvent preEvent = eventBuilder(Phase.PRE, method, ctx).build();
        listeners.forEach(l -> l.onEvent(preEvent));

        // call the callable and measure the duration
        T result = null;
        Exception error = null;
        long startedAt = System.currentTimeMillis();
        try {
            result = callable.call();
        } catch (Exception e) {
            error = e;
        }
        long duration = System.currentTimeMillis() - startedAt;

        // record the POST event
        TaskCallEvent postEvent = eventBuilder(Phase.POST, method, ctx)
                .error(errorMessage(error))
                .duration(duration)
                .result(result instanceof Serializable ? (Serializable) result : null)
                .build();
        listeners.forEach(l -> l.onEvent(postEvent));

        if (error != null) {
            throw error;
        }

        return result;
    }

    private static String errorMessage(Exception e) {
        if (e == null) {
            return null;
        }

        if (e.getMessage() != null) {
            return e.getMessage();
        }

        return "Error type: " + e.getClass().getName();
    }

    private static ImmutableTaskCallEvent.Builder eventBuilder(Phase phase, Method method, CallContext ctx) {
        return TaskCallEvent.builder()
                .phase(phase)
                .correlationId(ctx.correlationId())
                .currentStep(ctx.currentStep())
                .input(method.arguments())
                .methodName(method.name())
                .processDefinition(ctx.processDefinition())
                .taskName(ctx.taskName());
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    public interface Method {

        String name();

        @AllowNulls
        @Value.Default
        default List<Object> arguments() {
            return Collections.emptyList();
        }

        static Method of(String name, Object... arguments) {
            return ImmutableMethod.builder()
                    .name(name)
                    .addArguments(arguments)
                    .build();
        }
    }

    @Value.Immutable
    public interface CallContext {

        String taskName();

        UUID correlationId();

        Step currentStep();

        ProcessDefinition processDefinition();

        static ImmutableCallContext.Builder builder() {
            return ImmutableCallContext.builder();
        }
    }
}
