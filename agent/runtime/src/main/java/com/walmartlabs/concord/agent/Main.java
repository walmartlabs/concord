package com.walmartlabs.concord.agent;

import com.google.inject.Injector;
import com.walmartlabs.concord.bootstrap.Bootstrap;
import com.walmartlabs.concord.bootstrap.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class Main {

    private Server server;

    public void start() throws Exception {
        server = new Server(8002) {
            @Override
            protected Injector createInjector(ServletContextHandler h) {
                return Bootstrap.createInjector(Main.class.getClassLoader());
            }
        };

        server.start();
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    public static void main(String[] args) throws Exception {
        Main m = new Main();
        m.start();
    }
}
