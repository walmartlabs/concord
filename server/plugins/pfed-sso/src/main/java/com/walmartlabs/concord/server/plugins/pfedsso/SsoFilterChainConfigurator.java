package com.walmartlabs.concord.server.plugins.pfedsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

public class SsoFilterChainConfigurator implements FilterChainConfigurator {

    private final SsoAuthFilter ssoAuthFilter;
    private final SsoCallbackFilter ssoCallbackFilter;
    private final SsoLogoutFilter ssoLogoutFilter;
    private final SsoConfiguration ssoConfiguration;

    @Inject
    public SsoFilterChainConfigurator(SsoAuthFilter ssoAuthFilter,
                                      SsoCallbackFilter ssoCallbackFilter,
                                      SsoLogoutFilter ssoLogoutFilter,
                                      SsoConfiguration ssoConfiguration) {

        this.ssoAuthFilter = ssoAuthFilter;
        this.ssoCallbackFilter = ssoCallbackFilter;
        this.ssoLogoutFilter = ssoLogoutFilter;
        this.ssoConfiguration = ssoConfiguration;
    }

    @Override
    public void configure(FilterChainManager manager) {
        manager.addFilter("ssoAuth", ssoAuthFilter);
        manager.createChain("/api/service/sso/auth", "ssoAuth");

        manager.addFilter("ssoCallback", ssoCallbackFilter);
        manager.createChain("/api/service/sso/redirect", "ssoCallback");

        manager.addFilter("ssoLogout", ssoLogoutFilter);
        manager.createChain("/api/service/sso/logout", "ssoLogout");
    }

    @Override
    public int priority() {
        int priority = ssoConfiguration.getPriority();
        if (!ssoConfiguration.isEnabled())
            return priority + 1;
        return priority;
    }
}
