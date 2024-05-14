package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2020 Ivan Bodrov
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

import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.inject.Inject;

public class OidcFilterChainConfigurator implements FilterChainConfigurator {

    private final OidcAuthFilter authFilter;
    private final OidcCallbackFilter callbackFilter;
    private final OidcLogoutFilter logoutFilter;

    @Inject
    public OidcFilterChainConfigurator(OidcAuthFilter authFilter,
                                       OidcCallbackFilter callbackFilter,
                                       OidcLogoutFilter logoutFilter) {

        this.authFilter = authFilter;
        this.callbackFilter = callbackFilter;
        this.logoutFilter = logoutFilter;
    }

    @Override
    public void configure(FilterChainManager manager) {
        manager.addFilter("oidcAuth", authFilter);
        manager.createChain(OidcAuthFilter.URL, "oidcAuth");

        manager.addFilter("oidcCallback", callbackFilter);
        manager.createChain(OidcCallbackFilter.URL, "oidcCallback");

        manager.addFilter("oidcLogout", logoutFilter);
        manager.createChain(OidcLogoutFilter.URL, "oidcLogout");
    }
}
