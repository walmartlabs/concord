package com.walmartlabs.concord.server;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.walmartlabs.concord.common.bootstrap.Bootstrap;
import com.walmartlabs.concord.common.server.Server;
import com.walmartlabs.concord.server.security.SecurityModule;
import org.apache.shiro.guice.aop.ShiroAopModule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        long t1 = System.currentTimeMillis();

        Server server = new Server(8001, true) {

            @Override
            protected void configureRewriteHandler(RewriteHandler h) {
                // backwards compatibility
                RewriteRegexRule logsRule = new RewriteRegexRule();
                logsRule.setRegex("/logs/(.*)\\.log");
                logsRule.setReplacement("/api/v1/process/$1/log");
                h.addRule(logsRule);
            }

            @Override
            protected void configureServletContext(ServletContextHandler h, Injector i) {
                for (Key<?> k : i.getAllBindings().keySet()) {
                    if (ServletConfigurer.class.isAssignableFrom(k.getTypeLiteral().getRawType())) {
                        ServletConfigurer s = (ServletConfigurer) i.getInstance(k);
                        s.configure(h);
                    }
                }
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
