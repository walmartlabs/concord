package com.walmartlabs.concord.server.org;

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
import com.walmartlabs.concord.server.org.inventory.InventoryModule;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreModule;
import com.walmartlabs.concord.server.org.policy.PolicyModule;
import com.walmartlabs.concord.server.org.project.ProjectModule;
import com.walmartlabs.concord.server.org.secret.SecretModule;
import com.walmartlabs.concord.server.org.team.TeamModule;
import com.walmartlabs.concord.server.org.triggers.TriggersModule;

import static com.google.inject.Scopes.SINGLETON;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

public class OrganizationModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(OrganizationDao.class).in(SINGLETON);
        binder.bind(OrganizationManager.class).in(SINGLETON);

        bindJaxRsResource(binder, OrganizationResource.class);
        bindJaxRsResource(binder, ProjectProcessResource.class);

        binder.install(new InventoryModule());
        binder.install(new JsonStoreModule());
        binder.install(new PolicyModule());
        binder.install(new ProjectModule());
        binder.install(new SecretModule());
        binder.install(new TeamModule());
        binder.install(new TriggersModule());
    }
}
