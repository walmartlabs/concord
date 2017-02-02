package com.walmartlabs.concord.agent;

import com.google.inject.Injector;
import com.walmartlabs.concord.common.bootstrap.Bootstrap;
import com.walmartlabs.concord.common.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class Main {

    private Server server;

    public void start() throws Exception {
        start(8002);
    }

    public void start(int port) throws Exception {
        server = new Server(port) {
            @Override
            protected Injector createInjector(ServletContextHandler h) {
                return Bootstrap.createInjector(Main.class.getClassLoader());
            }
        };

        server.start();
    }

    public int getLocalPort() {
        if (server == null) {
            return -1;
        }

        return server.getLocalPort();
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
