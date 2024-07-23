package com.walmartlabs.concord.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import com.walmartlabs.concord.server.boot.filters.ConcordAuthenticatingFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.inject.Inject;

public class ConsoleFilterChainConfigurator implements FilterChainConfigurator {

    private final ConcordAuthenticatingFilter concordDelegate;

    @Inject
    public ConsoleFilterChainConfigurator(ConcordAuthenticatingFilter concordDelegate) {
        this.concordDelegate = concordDelegate;
    }

    @Override
    public void configure(FilterChainManager manager) {
        manager.createChain("/console3/**", "anon");

        manager.addFilter("console3", concordDelegate);
        manager.addFilter("console3-user-session", new UserContextFilter());
        manager.createChain("/api/console3/**", "console3, console3-user-session");
    }
}
