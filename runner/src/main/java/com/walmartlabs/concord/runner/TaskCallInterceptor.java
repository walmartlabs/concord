package com.walmartlabs.concord.runner;

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


import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.TaskRule;
import com.walmartlabs.concord.sdk.Task;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class TaskCallInterceptor implements MethodInterceptor {

    public static final Matcher<? super Class<?>> CLASS_MATCHER = new AbstractMatcher<Class<?>>() {
        @Override
        public boolean matches(Class<?> aClass) {
            return Task.class.isAssignableFrom(aClass);
        }
    };

    public static final AbstractMatcher<Method> METHOD_MATCHER = new AbstractMatcher<Method>() {
        @Override
        public boolean matches(Method method) {
            return !method.isSynthetic();
        }
    };

    private static final Logger log = LoggerFactory.getLogger(TaskCallInterceptor.class);

    private final PolicyEngineHolder holder;

    public TaskCallInterceptor(PolicyEngineHolder holder) {
        this.holder = holder;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (holder.getEngine() == null) {
            return invocation.proceed();
        }

        Named n = findAnnotation(invocation.getThis().getClass(), Named.class);
        if (n == null) {
            return invocation.proceed();
        }

        CheckResult<TaskRule, String> result = holder.getEngine().getTaskPolicy().check(
                n.value(),
                invocation.getMethod().getName(),
                invocation.getArguments());

        result.getWarn().forEach(d -> {
            log.warn("Potentially restricted task call '{}' (task policy {})", n.value(), d.getRule().toString());
        });
        result.getDeny().forEach(d -> {
            log.error("Task call '{}' is forbidden by the task policy {}", n.value(), d.getRule().toString());
        });

        if (!result.getDeny().isEmpty()) {
            throw new RuntimeException("Found forbidden tasks");
        }

        return invocation.proceed();
    }

    private static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
        A annotation = clazz.getAnnotation(annotationType);
        if (annotation != null) {
            return annotation;
        }

        for (Class<?> ifc : clazz.getInterfaces()) {
            annotation = findAnnotation(ifc, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass == null || superClass == Object.class) {
            return null;
        }

        return findAnnotation(superClass, annotationType);
    }
}
