package com.walmartlabs.concord.server;

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
import com.walmartlabs.concord.server.boot.*;
import com.walmartlabs.concord.server.boot.filters.*;
import com.walmartlabs.concord.server.boot.resteasy.ResteasyModule;
import com.walmartlabs.concord.server.boot.servlets.FormServletHolder;
import com.walmartlabs.concord.server.boot.statics.StaticResourcesConfigurator;
import com.walmartlabs.concord.server.boot.validation.ValidationModule;
import com.walmartlabs.concord.server.websocket.ConcordWebSocketServlet;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.eclipse.jetty.ee8.servlet.FilterHolder;

import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServlet;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.*;

public class ApiServerModule implements Module {

    @Override
    public void configure(Binder binder) {
        // Jetty

        binder.bind(HttpServer.class).in(SINGLETON);

        // Filter

        bindServletFilter(binder, ConcordAuthenticatingFilter.class);
        bindServletFilter(binder, CORSFilter.class);
        bindServletFilter(binder, NoCacheFilter.class);
        bindServletFilter(binder, QoSFilter.class);
        bindServletFilter(binder, RequestContextFilter.class);

        // FilterHolder

        newSetBinder(binder, FilterHolder.class).addBinding().to(ShiroFilterHolder.class).in(SINGLETON);

        // HttpServlet

        newSetBinder(binder, HttpServlet.class).addBinding().to(ConcordWebSocketServlet.class).in(SINGLETON);

        // ServletHolder

        bindServletHolder(binder, FormServletHolder.class);

        // RequestErrorHandler

        newSetBinder(binder, RequestErrorHandler.class).addBinding().to(FormRequestErrorHandler.class);
        newSetBinder(binder, RequestErrorHandler.class).addBinding().to(GenericRequestErrorHandler.class);

        // ContextHandlerConfigurator

        newSetBinder(binder, ContextHandlerConfigurator.class).addBinding().to(StaticResourcesConfigurator.class);

        // Shiro

        newSetBinder(binder, ServletContextListener.class).addBinding().to(ShiroListener.class).in(SINGLETON);
        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(ConcordFilterChainConfigurator.class).in(SINGLETON);

        binder.bind(ConcordSecurityManager.class).in(SINGLETON);
        binder.bind(SecurityManager.class).to(ConcordSecurityManager.class);
        binder.bind(WebSecurityManager.class).to(ConcordSecurityManager.class);

        binder.install(new ValidationModule());

        // Resteasy

        binder.install(new ResteasyModule());

        // JAX-RS resources

        bindJaxRsResource(binder, ServerResource.class);
    }
}
