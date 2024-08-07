package com.walmartlabs.concord.server.plugins.ansible;

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
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.plugins.ansible.db.AnsibleDBChangeLogProvider;
import com.walmartlabs.concord.server.plugins.ansible.queue.InventoryProcessor;
import com.walmartlabs.concord.server.plugins.ansible.queue.PrivateKeyProcessor;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.sdk.process.CustomEnqueueProcessor;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class AnsibleModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(DatabaseChangeLogProvider.class).annotatedWith(MainDB.class).to(AnsibleDBChangeLogProvider.class);
        binder.bind(EventFetcher.class).in(SINGLETON);
        newSetBinder(binder, ScheduledTask.class).addBinding().to(EventFetcher.class);
        newSetBinder(binder, CustomEnqueueProcessor.class).addBinding().to(PrivateKeyProcessor.class);
        newSetBinder(binder, CustomEnqueueProcessor.class).addBinding().to(InventoryProcessor.class);
    }
}
