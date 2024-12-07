package com.walmartlabs.concord.server.console3;

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

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import com.walmartlabs.concord.server.boot.filters.ConcordAuthenticatingFilter;
import com.walmartlabs.concord.server.boot.resteasy.ApiDescriptor;
import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;
import static com.walmartlabs.concord.server.Utils.bindServletContextListener;

public class ConsoleModule implements Module {

    @Override
    public void configure(Binder binder) {
        bindServletContextListener(binder, ThymeleafApp.class);
        bindJaxRsResource(binder, ConsoleResource.class);
        newSetBinder(binder, ApiDescriptor.class).addBinding().to(Console3ApiDescriptor.class);
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(Console3AuthenticationHandler.class);
        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(Console3FilterChainConfigurator.class);
    }

    /**
     * Prefix for UI endpoints.
     */
    static class Console3ApiDescriptor implements ApiDescriptor {

        @Override
        public String[] paths() {
            return new String[]{"/console3/*"};
        }
    }

    /**
     * Use the standard built-in authentication.
     */
    static class Console3FilterChainConfigurator implements FilterChainConfigurator {

        private final ConcordAuthenticatingFilter delegate;

        @Inject
        public Console3FilterChainConfigurator(ConcordAuthenticatingFilter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void configure(FilterChainManager manager) {
            manager.addFilter("console3", delegate);
            manager.createChain("/console3/**", "console3");
        }
    }

    /**
     * Prevents the default behaviour of sending Basic authentication prompt for non-legacy-UI requests.
     * We do not want those popups.
     */
    static class Console3AuthenticationHandler implements AuthenticationHandler {

        @Override
        public boolean onAccessDenied(ServletRequest request, ServletResponse response) {
            var req = (HttpServletRequest) request;
            var uri = req.getRequestURI();
            return uri.startsWith("/console3/");
        }
    }
}
