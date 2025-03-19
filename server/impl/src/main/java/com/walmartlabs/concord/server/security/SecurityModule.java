package com.walmartlabs.concord.server.security;

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
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import com.walmartlabs.concord.server.security.apikey.ApiKeyAuthenticationHandler;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import com.walmartlabs.concord.server.security.github.GithubRealm;
import com.walmartlabs.concord.server.security.internal.InternalRealm;
import com.walmartlabs.concord.server.security.internal.LocalUserInfoProvider;
import com.walmartlabs.concord.server.security.ldap.*;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.ldap.LdapContextFactory;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindExceptionMapper;
import static com.walmartlabs.concord.server.Utils.bindSingletonScheduledTask;

public class SecurityModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(BasicAuthenticationHandler.class).in(SINGLETON);
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(ApiKeyAuthenticationHandler.class).in(SINGLETON);
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(SessionTokenAuthenticationHandler.class).in(SINGLETON);

        newSetBinder(binder, Realm.class).addBinding().to(ApiKeyRealm.class);
        newSetBinder(binder, Realm.class).addBinding().to(GithubRealm.class);
        newSetBinder(binder, Realm.class).addBinding().to(InternalRealm.class);
        newSetBinder(binder, Realm.class).addBinding().to(LdapRealm.class);
        newSetBinder(binder, Realm.class).addBinding().to(SessionKeyRealm.class);

        binder.bind(LdapManager.class).toProvider(LdapManagerProvider.class);
        binder.bind(LdapContextFactory.class).toProvider(LdapContextFactoryProvider.class);

        bindSingletonScheduledTask(binder, UserLdapGroupSynchronizer.class);

        binder.bind(LocalUserInfoProvider.class).in(SINGLETON);
        newSetBinder(binder, UserInfoProvider.class).addBinding().to(LocalUserInfoProvider.class);

        binder.bind(LdapUserInfoProvider.class).in(SINGLETON);
        newSetBinder(binder, UserInfoProvider.class).addBinding().to(LdapUserInfoProvider.class);

        bindExceptionMapper(binder, UnauthorizedExceptionMapper.class);
        bindExceptionMapper(binder, UnauthenticatedExceptionMapper.class);
    }
}
