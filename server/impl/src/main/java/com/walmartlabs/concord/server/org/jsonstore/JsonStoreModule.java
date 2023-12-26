package com.walmartlabs.concord.server.org.jsonstore;

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

public class JsonStoreModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(JsonStoreDao.class).in(SINGLETON);
        binder.bind(JsonStoreManager.class).in(SINGLETON);
        binder.bind(JsonStoreAccessManager.class).in(SINGLETON);
        binder.bind(JsonStoreDataDao.class).in(SINGLETON);
        binder.bind(JsonStoreDataManager.class).in(SINGLETON);
        binder.bind(JsonStoreQueryDao.class).in(SINGLETON);
        binder.bind(JsonStoreQueryExecDao.class).in(SINGLETON);
        binder.bind(JsonStoreQueryManager.class).in(SINGLETON);


        bindJaxRsResource(binder, JsonStoreResource.class);
        bindJaxRsResource(binder, JsonStoreDataResource.class);
        bindJaxRsResource(binder, JsonStoreQueryResource.class);
    }
}
