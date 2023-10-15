package com.walmartlabs.concord.server.plugins.pfedsso;

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
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import org.apache.shiro.realm.Realm;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class PluginModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, UserInfoProvider.class).addBinding().to(SsoUserInfoProvider.class);
        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(SsoFilterChainConfigurator.class);
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(SsoHandler.class);
        newSetBinder(binder, Realm.class).addBinding().to(SsoRealm.class);

        binder.bind(JwtAuthenticator.class).in(SINGLETON);
    }
}
