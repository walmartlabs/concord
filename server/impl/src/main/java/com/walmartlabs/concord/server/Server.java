package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

public abstract class Server {

    private final int port;
    private final boolean sessionsEnabled;

    private org.eclipse.jetty.server.Server server;

    public Server(int port) {
        this(port, false);
    }

    public Server(int port, boolean sessionsEnabled) {
        this.port = port;
        this.sessionsEnabled = sessionsEnabled;
    }

    public void start() throws Exception {
        server = new org.eclipse.jetty.server.Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(createHandler());

        server.start();
    }

    public int getLocalPort() {
        if (server == null) {
            return -1;
        }

        ServerConnector c = (ServerConnector) server.getConnectors()[0];
        return c.getLocalPort();
    }

    private Handler createHandler() {
        RewriteHandler rewrite = new RewriteHandler();
        configureRewriteHandler(rewrite);
        rewrite.setHandler(createServletHandler());
        return rewrite;
    }

    private Handler createServletHandler() {
        int options = 0;
        if (sessionsEnabled) {
            options |= ServletContextHandler.SESSIONS;
        }
        ServletContextHandler h = new ServletContextHandler(options);

        Injector i = createInjector(h);
        h.addEventListener(new GuiceServletContextListener() {
            @Override
            protected Injector getInjector() {
                return i;
            }
        });

        FilterHolder filter = new FilterHolder(GuiceFilter.class);
        h.addFilter(filter, "/*", null);

        configureServletContext(h, i);

        return h;
    }

    protected void configureRewriteHandler(RewriteHandler h) {
    }

    protected void configureServletContext(ServletContextHandler h, Injector i) {
    }

    protected abstract Injector createInjector(ServletContextHandler h);

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}
