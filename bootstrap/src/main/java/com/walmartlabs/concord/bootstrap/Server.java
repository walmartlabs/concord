package com.walmartlabs.concord.bootstrap;

import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

public abstract class Server {

    private final int port;

    private org.eclipse.jetty.server.Server server;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        server = new org.eclipse.jetty.server.Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(createServletContextHandler());

        server.start();
    }

    private ServletContextHandler createServletContextHandler() {
        ServletContextHandler h = new ServletContextHandler();

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

    protected void configureServletContext(ServletContextHandler h, Injector i) {
    }

    protected abstract Injector createInjector(ServletContextHandler h);

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}
