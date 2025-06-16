package com.walmartlabs.concord.server.boot.filters;

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
import com.walmartlabs.concord.server.security.GithubAuthenticatingFilter;
import com.walmartlabs.concord.server.security.LocalRequestFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.inject.Inject;

public class ConcordFilterChainConfigurator implements FilterChainConfigurator {

    private final ConcordAuthenticatingFilter concordAuthenticatingFilter;
    private final GithubAuthenticatingFilter githubAuthenticatingFilter;

    @Inject
    public ConcordFilterChainConfigurator(ConcordAuthenticatingFilter concordAuthenticatingFilter,
                                          GithubAuthenticatingFilter githubAuthenticatingFilter) {

        this.concordAuthenticatingFilter = concordAuthenticatingFilter;
        this.githubAuthenticatingFilter = githubAuthenticatingFilter;
    }

    @Override
    public void configure(FilterChainManager manager) {
        // allow access w/o auth
        manager.createChain("/api/v1/server/ping", "anon");
        manager.createChain("/api/v1/server/version", "anon");
        manager.createChain("/api/service/console/logout", "anon");
        manager.createChain("/api/service/console/cfg", "anon");

        // local requests only
        manager.addFilter("local", new LocalRequestFilter());
        manager.createChain("/api/v1/server/maintenance-mode", "local");

        // regular auth
        manager.addFilter("concord", concordAuthenticatingFilter);
        manager.createChain("/api/**", "concord");
        manager.createChain("/forms/**", "concord");

        // special auth for GitHub
        manager.addFilter("github", githubAuthenticatingFilter);
        manager.createChain("/events/github/**", "github");
    }

    @Override
    public int priority() {
        // because most of the filters here are using "wide" patterns
        // like /api/** we need to run this configurator last
        return 100;
    }
}
