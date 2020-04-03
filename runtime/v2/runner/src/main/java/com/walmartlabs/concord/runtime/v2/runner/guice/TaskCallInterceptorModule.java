package com.walmartlabs.concord.runtime.v2.runner.guice;

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

import com.google.inject.AbstractModule;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import java.lang.reflect.Method;

public class TaskCallInterceptorModule extends AbstractModule {

    private static final Matcher<? super Class<?>> V2_TASKS = Matchers.subclassesOf(Task.class);

    private static final AbstractMatcher<Method> METHOD_MATCHER = new AbstractMatcher<Method>() {
        @Override
        public boolean matches(Method method) {
            return !method.isSynthetic();
        }
    };

    @Override
    protected void configure() {
        TaskCallInterceptor interceptor = new TaskCallInterceptor();
        requestInjection(interceptor);

        bindInterceptor(V2_TASKS, METHOD_MATCHER, interceptor);
    }
}
