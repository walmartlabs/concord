package com.walmartlabs.concord.server.org.secret;

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
import com.walmartlabs.concord.server.org.secret.provider.SecretStoreProvider;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.org.secret.store.concord.ConcordSecretStore;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindExceptionMapper;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

public class SecretModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(SecretManager.class).in(SINGLETON);
        binder.bind(SecretDao.class).in(SINGLETON);
        binder.bind(SecretStoreProvider.class).in(SINGLETON);

        newSetBinder(binder, SecretStore.class).addBinding().to(ConcordSecretStore.class);

        bindJaxRsResource(binder, SecretStoreResource.class);
        bindJaxRsResource(binder, SecretResource.class);
        bindJaxRsResource(binder, SecretResourceV2.class);

        bindExceptionMapper(binder, SecretExceptionMapper.class);
    }
}
