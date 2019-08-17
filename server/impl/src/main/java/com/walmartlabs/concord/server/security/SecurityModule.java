package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.inject.Key;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import com.walmartlabs.concord.server.security.github.GithubRealm;
import com.walmartlabs.concord.server.security.ldap.LdapRealm;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import org.apache.shiro.guice.web.ShiroWebModule;

import javax.servlet.ServletContext;

public class SecurityModule extends ShiroWebModule {

    public SecurityModule(ServletContext servletContext) {
        super(servletContext);
    }

    @Override
    protected void configureShiroWeb() {
        bindRealm().to(ApiKeyRealm.class);
        bindRealm().to(LdapRealm.class);
        bindRealm().to(SessionKeyRealm.class);
        bindRealm().to(GithubRealm.class);

        addFilterChain("/api/**", Key.get(ConcordAuthenticatingFilter.class));
        addFilterChain("/forms/**", Key.get(ConcordAuthenticatingFilter.class));
        addFilterChain("/jolokia/**", Key.get(ConcordAuthenticatingFilter.class));
        addFilterChain("/events/github/**", Key.get(GithubAuthenticatingFilter.class));
    }
}
