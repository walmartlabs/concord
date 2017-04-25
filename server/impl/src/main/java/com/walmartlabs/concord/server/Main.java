package com.walmartlabs.concord.server;

import com.google.inject.Injector;
import com.walmartlabs.concord.common.bootstrap.Bootstrap;
import com.walmartlabs.concord.common.server.Server;
import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import com.walmartlabs.concord.server.security.SecurityModule;
import org.apache.shiro.guice.aop.ShiroAopModule;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        long t1 = System.currentTimeMillis();

        Server server = new Server(8001, true) {
            @Override
            protected void configureServletContext(ServletContextHandler h, Injector i) {
                LogServletConfigurer logCfg = new LogServletConfigurer(i.getInstance(LogStoreConfiguration.class));
                logCfg.configure(h);

                SwaggerServletConfigurer swaggerCfg = new SwaggerServletConfigurer();
                swaggerCfg.configure(h);
            }

            @Override
            protected Injector createInjector(ServletContextHandler h) {
                return Bootstrap.createInjector(Main.class.getClassLoader(),
                        new SecurityModule(h.getServletContext()),
                        new ShiroAopModule());
            }
        };

        server.start();

        long t2 = System.currentTimeMillis();
        log.info("main -> started in {}ms", (t2 - t1));
    }
}
