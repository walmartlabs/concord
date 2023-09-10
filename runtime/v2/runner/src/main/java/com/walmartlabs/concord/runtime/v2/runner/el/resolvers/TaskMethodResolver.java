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
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.el.ELContext;
import javax.inject.Named;
import java.util.Arrays;

import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.CallContext;
import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.Method;

public class TaskMethodResolver extends javax.el.BeanELResolver {

    private final Context context;

    public TaskMethodResolver(Context context) {
        this.context = context;
    }

    @Override
    public Object invoke(ELContext elContext, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        Step step = context.execution().currentStep();
        if (!(step instanceof Expression)
                || !(base instanceof Task)
                || !(method instanceof String)) {
            return null;
        }

        String taskName = getName(base);
        if (taskName == null) {
            return null;
        }

        CallContext callContext = TaskCallInterceptor.CallContext.builder()
                .threadId(context.execution().currentThreadId())
                .taskName(taskName)
                .correlationId(context.execution().correlationId())
                .currentStep(step)
                .processDefinition(context.execution().processDefinition())
                .build();

        TaskCallInterceptor interceptor = context.execution().runtime().getService(TaskCallInterceptor.class);
        try {
            return interceptor.invoke(callContext, Method.of(base, (String)method, Arrays.asList(params)),
                    () -> super.invoke(elContext, base, method, paramTypes, params));
        } catch (javax.el.MethodNotFoundException e) {
            throw new MethodNotFoundException(base, method, paramTypes);
        } catch (javax.el.ELException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (TaskException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getName(Object task) {
        Named n = ReflectionUtils.findAnnotation(task.getClass(), Named.class);
        if (n != null) {
            return n.value();
        }

        return null;
    }
}
