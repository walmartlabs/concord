package com.walmartlabs.concord.server.org.triggers;

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
import com.google.inject.Module;

import static com.google.inject.Scopes.SINGLETON;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;
import static com.walmartlabs.concord.server.Utils.bindSingletonScheduledTask;

public class TriggersModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(CronTriggerProcessor.class).in(SINGLETON);
        binder.bind(GithubTriggerEnricher.class).in(SINGLETON);
        binder.bind(TriggerManager.class).in(SINGLETON);
        binder.bind(TriggerScheduleDao.class).in(SINGLETON);
        binder.bind(TriggersDao.class).in(SINGLETON);

        bindJaxRsResource(binder, TriggerResource.class);
        bindJaxRsResource(binder, TriggerV2Resource.class);

        bindSingletonScheduledTask(binder, TriggerScheduler.class);
    }
}
