package com.walmartlabs.concord.server.security.ldap;

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
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.sdk.security.AuthorizationProvider;
import com.walmartlabs.concord.server.sdk.security.ProvisioningPolicy;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.ldap.LdapContextFactory;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindSingletonScheduledTask;

@Named
public class LdapPluginModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(LdapConfiguration.class).in(SINGLETON);
        binder.bind(LdapGroupSyncConfiguration.class).in(SINGLETON);
        binder.bind(LdapManager.class).toProvider(LdapManagerProvider.class);
        binder.bind(LdapContextFactory.class).toProvider(LdapContextFactoryProvider.class);

        newSetBinder(binder, UserDirectory.class).addBinding().to(LdapUserDirectory.class);
        newSetBinder(binder, AuthorizationProvider.class).addBinding().to(LdapAuthorizationProvider.class);
        newSetBinder(binder, ProvisioningPolicy.class).addBinding().toProvider(LdapProvisioningPolicyProvider.class);
        newSetBinder(binder, Realm.class).addBinding().to(LdapRealm.class);
        bindSingletonScheduledTask(binder, UserLdapGroupSynchronizer.class);
    }
}
