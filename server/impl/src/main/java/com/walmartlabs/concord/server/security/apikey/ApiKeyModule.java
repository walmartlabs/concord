package com.walmartlabs.concord.server.security.apikey;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.concord.server.security.apikey.loader.ApiKeyLoader;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;
import static com.walmartlabs.concord.server.Utils.bindSingletonScheduledTask;

public class ApiKeyModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(ApiKeyLoader.class).in(SINGLETON);
        newSetBinder(binder, BackgroundTask.class).addBinding().to(ApiKeyLoader.class);

        bindJaxRsResource(binder, ApiKeyResource.class);

        bindSingletonScheduledTask(binder, ApiKeyCleaner.class);
        bindSingletonScheduledTask(binder, ApiKeyExpirationNotifier.class);
    }
}
