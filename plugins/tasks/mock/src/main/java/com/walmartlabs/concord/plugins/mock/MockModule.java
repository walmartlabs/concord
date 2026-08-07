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

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;

import javax.inject.Named;

@Named
public class MockModule implements com.google.inject.Module {

    @Override
    public void configure(Binder binder) {
        var taskProviders = Multibinder.newSetBinder(binder, TaskProvider.class);
        taskProviders.addBinding().to(MockTaskProvider.class);

        var taskMethodResolvers = Multibinder.newSetBinder(binder, CustomTaskMethodResolver.class);
        taskMethodResolvers.addBinding().to(MockTaskMethodResolver.class);

        var beanMethodResolvers = Multibinder.newSetBinder(binder, CustomBeanMethodResolver.class);
        beanMethodResolvers.addBinding().to(VerifierBeanMethodResolver.class);

        bindV2InvocationCollectors(binder);
    }

    private static void bindV2InvocationCollectors(Binder binder) {
        try {
            var classLoader = MockModule.class.getClassLoader();
            var taskCallListener = Class.forName("com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener",
                    false, classLoader);
            var executionListener = Class.forName("com.walmartlabs.concord.svm.ExecutionListener", false, classLoader);
            var collector = Class.forName("com.walmartlabs.concord.plugins.mock.InvocationsCollector", false, classLoader);
            addListener(binder, taskCallListener, collector);
            addListener(binder, executionListener, collector);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Invocation verification uses runtime-v2 listener APIs and is unavailable without them.
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addListener(Binder binder, Class<?> listenerType, Class<?> implementationType) {
        Multibinder.newSetBinder(binder, (Class) listenerType).addBinding().to((Class) implementationType);
    }
}
