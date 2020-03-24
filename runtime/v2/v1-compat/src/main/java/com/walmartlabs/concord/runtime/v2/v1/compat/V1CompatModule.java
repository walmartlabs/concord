package com.walmartlabs.concord.runtime.v2.v1.compat;

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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.runtime.common.injector.InjectorUtils;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.sdk.Task;

/**
 * Runtime v1 compatibility module for Runtime v2.
 */
public class V1CompatModule extends AbstractModule {

    @Override
    protected void configure() {
        TaskHolder<Task> holder = new TaskHolder<>();
        bindListener(InjectorUtils.subClassesOf(Task.class), InjectorUtils.taskClassesListener(holder));

        bind(new TypeLiteral<TaskHolder<Task>>() {
        }).annotatedWith(V1.class).toInstance(holder);

        Multibinder<TaskProvider> taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
        taskProviders.addBinding().to(TaskV1Provider.class);

        bind(com.walmartlabs.concord.sdk.SecretService.class).to(SecretServiceV1Impl.class);
        bind(com.walmartlabs.concord.sdk.DockerService.class).to(DockerServiceV1Impl.class);
    }
}
