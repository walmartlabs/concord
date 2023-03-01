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
import com.walmartlabs.concord.server.boot.servlets.ConsoleServletHolder;
import com.walmartlabs.concord.server.boot.servlets.FormServletHolder;
import com.walmartlabs.concord.server.boot.servlets.SiestaServletHolder;
import com.walmartlabs.concord.server.boot.statics.StaticResourcesConfigurator;
import com.walmartlabs.concord.server.websocket.ConcordWebSocketServlet;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.Filter;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServlet;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class ApiServerModule implements Module {

    @Override
    public void configure(Binder binder) {
        // Jetty
        binder.bind(HttpServer.class).in(SINGLETON);

        // Filter

        binder.bind(ConcordAuthenticatingFilter.class).in(SINGLETON);
        newSetBinder(binder, Filter.class).addBinding().to(ConcordAuthenticatingFilter.class);
        newSetBinder(binder, Filter.class).addBinding().to(CORSFilter.class);
        newSetBinder(binder, Filter.class).addBinding().to(NoCacheFilter.class);
        newSetBinder(binder, Filter.class).addBinding().to(QoSFilter.class);
        newSetBinder(binder, Filter.class).addBinding().to(RequestContextFilter.class);

        // FilterHolder

        newSetBinder(binder, FilterHolder.class).addBinding().to(ShiroFilterHolder.class);

        // HttpServlet

        newSetBinder(binder, HttpServlet.class).addBinding().to(LogServlet.class);
        newSetBinder(binder, HttpServlet.class).addBinding().to(ConcordWebSocketServlet.class);

        // ServletHolder

        newSetBinder(binder, ServletHolder.class).addBinding().to(ConsoleServletHolder.class);
        newSetBinder(binder, ServletHolder.class).addBinding().to(FormServletHolder.class);
        newSetBinder(binder, ServletHolder.class).addBinding().to(SiestaServletHolder.class);

        // RequestErrorHandler

        newSetBinder(binder, RequestErrorHandler.class).addBinding().to(FormRequestErrorHandler.class);

        // ContextHandlerConfigurator

        newSetBinder(binder, ContextHandlerConfigurator.class).addBinding().to(StaticResourcesConfigurator.class);

        // shiro

        newSetBinder(binder, ServletContextListener.class).addBinding().to(ShiroListener.class);
        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(ConcordFilterChainConfigurator.class);
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(ConcordAuthenticationHandler.class);

        binder.bind(ConcordSecurityManager.class).in(SINGLETON);
        binder.bind(SecurityManager.class).to(ConcordSecurityManager.class);
        binder.bind(WebSecurityManager.class).to(ConcordSecurityManager.class);

        // TODO get rid of RestModule, add all internal modules directly here
        binder.install(new RestModule());
    }
}
