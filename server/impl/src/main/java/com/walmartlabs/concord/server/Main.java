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
import com.google.inject.Key;
import com.walmartlabs.concord.server.security.SecurityModule;
import org.apache.shiro.guice.aop.ShiroAopModule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static com.walmartlabs.concord.server.SisuUtils.createSisuInjector;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        long t1 = System.currentTimeMillis();

        SLF4JBridgeHandler.install();

        int port = Integer.parseInt(Utils.getEnv("CONCORD_SERVER_PORT", "8001"));

        Server server = new Server(port, true) {

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
                return createSisuInjector(Main.class.getClassLoader(),
                        new SecurityModule(h.getServletContext()),
                        new ShiroAopModule());
            }
        };

        server.start();

        long t2 = System.currentTimeMillis();
        log.info("main -> started in {}ms", (t2 - t1));
    }
}
