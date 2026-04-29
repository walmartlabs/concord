package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.model.Expression;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.el.MethodNotFoundException;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskException;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.InvocationContext;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.inject.Named;
import java.beans.FeatureDescriptor;
import java.util.*;

import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.CallContext;
import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.Method;

public class TaskMethodResolver extends ELResolver {

    private final List<CustomTaskMethodResolver> resolvers = new ArrayList<>();
    private final Context context;
    private final SensitiveDataProcessor sensitiveDataProcessor;

    public TaskMethodResolver(List<CustomTaskMethodResolver> customResolvers,
                              Context context,
                              SensitiveDataProcessor sensitiveDataProcessor) {
        this.sensitiveDataProcessor = sensitiveDataProcessor;
        this.resolvers.addAll(customResolvers);
        this.resolvers.add(new DefaultTaskMethodResolver());
        this.context = context;
    }

    @Override
    public Object invoke(ELContext elContext, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        Step step = context.execution().currentStep();
        if (!(step instanceof Expression)
                || !(base instanceof Task task)
                || !(method instanceof String)) {
            return null;
        }

        var invocation = resolvers.stream()
                .map(resolver -> resolver.resolve(task, method.toString(), paramTypes, params))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (invocation == null) {
            return null;
        }

        CallContext callContext = TaskCallInterceptor.CallContext.builder()
                .threadId(context.execution().currentThreadId())
                .taskName(invocation.taskName())
                .correlationId(context.execution().correlationId())
                .currentStep(step)
                .processDefinition(context.execution().processDefinition())
                .build();

        TaskCallInterceptor interceptor = context.execution().runtime().getService(TaskCallInterceptor.class);
        try {
            return interceptor.invoke(callContext, Method.of(invocation.taskClass(), method.toString(), Arrays.asList(params)),
                    () -> {
                        var result = invocation.invoke(new DefaultInvocationContext(elContext, sensitiveDataProcessor));
                        elContext.setPropertyResolved(true);
                        return result;
                    });
        } catch (javax.el.MethodNotFoundException e) {
            throw new MethodNotFoundException(invocation.taskClass(), method, paramTypes);
        } catch (RuntimeException e) {
            throw e;
        } catch (TaskException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {

    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    private static class DefaultTaskMethodResolver implements CustomTaskMethodResolver {

        @Override
        public TaskInvocation resolve(Task base, String method, Class<?>[] paramTypes, Object[] params) {
            String taskName = getName(base);
            if (taskName == null) {
                return null;
            }
            return new DefaultInvocation(taskName, base, method, paramTypes, params);
        }

        private static String getName(Object task) {
            Named n = ReflectionUtils.findAnnotation(task.getClass(), Named.class);
            if (n != null) {
                return n.value();
            }

            return null;
        }
    }

    private record DefaultInvocation(String taskName, Task base, String method,
                                     Class<?>[] paramTypes, Object[] params) implements CustomTaskMethodResolver.TaskInvocation {

        @Override
        public Class<? extends Task> taskClass() {
            return base.getClass();
        }

        @Override
        public Object invoke(InvocationContext context) {
            return context.invoker().invoke(base, method, paramTypes, params);
        }
    }
}
