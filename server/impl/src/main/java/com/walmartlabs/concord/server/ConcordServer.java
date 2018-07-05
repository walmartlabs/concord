package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.walmartlabs.concord.server.cfg.CustomFormConfiguration;
import com.walmartlabs.concord.server.security.ConcordAuthenticatingFilter;
import com.walmartlabs.concord.server.security.GithubAuthenticatingFilter;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import com.walmartlabs.concord.server.security.github.GithubRealm;
import com.walmartlabs.concord.server.security.ldap.LdapRealm;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import com.walmartlabs.ollie.OllieServer;
import com.walmartlabs.ollie.guice.OllieServerBuilder;
import org.eclipse.jetty.servlet.DefaultServlet;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConcordServer {

    public void start() {
        OllieServerBuilder builder = OllieServer.builder()
                .port(8001)
                .apiPatterns("/api/*", "/events/github/*")
                .name("concord-server")
                .module(new ServerModule())
                .packageToScan("com.walmartlabs.concord.server")
                .realm(ApiKeyRealm.class)
                .realm(SessionKeyRealm.class)
                .realm(LdapRealm.class)
                .realm(GithubRealm.class)
                .filterChain("/api/**", ConcordAuthenticatingFilter.class)
                .filterChain("/forms/**", ConcordAuthenticatingFilter.class)
                .filterChain("/jolokia/**", ConcordAuthenticatingFilter.class)
                .filterChain("/events/github/*", GithubAuthenticatingFilter.class)
                .serve("/forms/*").with(DefaultServlet.class, formsServletParams())
                .serve("/logs/*").with(LogServlet.class) // backward compatibility
                .serve("/concord/*").with(new ServiceInitServlet()) // only to start the background services
                .filter("/service/*", "/api/*", "/logs/*", "/forms/*").through(CORSFilter.class)
                .filter("/service/*", "/api/*", "/logs/*", "/forms/*").through(NoCacheFilter.class)
                .sessionsEnabled(true);

        OllieServer server = builder.build();
        server.start();
    }

    private static Map<String, String> formsServletParams() {
        Map<String, String> m = new HashMap<>();

        m.put("acceptRanges", "true");
        m.put("dirAllowed", "false");
        m.put("resourceBase", CustomFormConfiguration.baseDir.toAbsolutePath().toString());
        m.put("pathInfoOnly", "true");
        m.put("redirectWelcome", "false");

        return m;
    }

    public static class LogServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String uri = req.getRequestURI();

            int i = uri.lastIndexOf("/");
            int len = uri.length();
            if (i < 0 || i + 1 >= len || !uri.endsWith(".log")) {
                throw new ServletException("Unknown request: " + uri);
            }

            String instanceId = uri.substring(i + 1, len - 4);
            RequestDispatcher dispatcher = req.getRequestDispatcher("/api/v1/process/" + instanceId + "/log");
            dispatcher.forward(req, resp);
        }
    }

    public static class ServiceInitServlet extends HttpServlet {

        @Inject
        Set<BackgroundTask> tasks;

        @Override
        public void init() throws ServletException {
            super.init();

            ServletContext ctx = getServletContext();
            Injector injector = (Injector) ctx.getAttribute(Injector.class.getName());

            injector.injectMembers(this);

            if (tasks != null) {
                tasks.forEach(BackgroundTask::start);
            }
        }

        @Override
        public void destroy() {
            if (tasks != null) {
                tasks.forEach(BackgroundTask::stop);
            }

            super.destroy();
        }
    }
}
