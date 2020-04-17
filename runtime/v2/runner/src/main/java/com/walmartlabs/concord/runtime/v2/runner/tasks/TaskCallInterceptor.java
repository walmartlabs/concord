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

import com.walmartlabs.concord.common.ReflectionUtils;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallEvent.Phase;
import com.walmartlabs.concord.runtime.v2.runner.vm.ThreadLocalContext;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Execution;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;

/**
 * Intercepts task calls and notifies {@link TaskCallListener}s.
 */
public class TaskCallInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TaskCallInterceptor.class);

    @Inject
    private List<TaskCallListener> listeners;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Named n = ReflectionUtils.findAnnotation(invocation.getThis().getClass(), Named.class);
        if (n == null) {
            return invocation.proceed();
        }

        String taskName = n.value();

        Context ctx = ThreadLocalContext.get();
        if (ctx == null || ctx.execution() == null || ctx.execution().currentStep() == null) {
            log.warn("invoke ['{}', {}] -> thread-local context, execution or currentStep is empty. " +
                    "This is most likely a bug.", taskName, invocation.getMethod());

            return invocation.proceed();
        }

        Execution execution = ctx.execution();

        long startedAt = System.currentTimeMillis();
        listeners.forEach(l -> l.onEvent(eventBuilder(Phase.PRE, execution, invocation, taskName)
                .build()));

        Object result = invocation.proceed();

        long duration = System.currentTimeMillis() - startedAt;
        listeners.forEach(l -> l.onEvent(eventBuilder(Phase.POST, execution, invocation, taskName)
                .duration(duration)
                .result((result instanceof Serializable) ? (Serializable)result : null)
                .build()));

        return result;
    }

    private static ImmutableTaskCallEvent.Builder eventBuilder(Phase phase, Execution execution, MethodInvocation invocation, String taskName) {
        return TaskCallEvent.builder()
                .phase(phase)
                .correlationId(execution.correlationId())
                .currentStep(execution.currentStep())
                .input(invocation.getArguments())
                .methodName(invocation.getMethod().getName())
                .processDefinition(execution.processDefinition())
                .taskName(taskName);
    }
}
