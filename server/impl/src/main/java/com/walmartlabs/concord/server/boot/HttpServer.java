package com.walmartlabs.concord.server.boot;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.ServerConfiguration;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.lang.management.ManagementFactory;
import java.util.EnumSet;
import java.util.Set;

public class HttpServer {

    private static final Logger log = LoggerFactory.getLogger(HttpServlet.class);

    private final Server server;

    @Inject
    public HttpServer(ServerConfiguration cfg,
                      Set<RequestErrorHandler> requestErrorHandlers,
                      Set<ServletContextListener> contextListeners,
                      Set<HttpServlet> servlets,
                      Set<ServletHolder> servletHolders,
                      Set<Filter> filters,
                      Set<FilterHolder> filterHolders,
                      Set<ContextHandlerConfigurator> contextHandlerConfigurators) {

        Server server = new Server();

        // init JMX
        MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        server.addBean(mbeanContainer);

        // configure the request log
        server.setRequestLog(createRequestLog(cfg));

        // init http transport
        HttpConfiguration httpCfg = new HttpConfiguration();
        httpCfg.setRequestHeaderSize(cfg.getRequestHeaderSize());
        httpCfg.addCustomizer(new ForwardedRequestCustomizer());

        int cores = Runtime.getRuntime().availableProcessors();
        int acceptors = Math.max(1, cores / 4);
        int selectors = -1; // use the default value
        ServerConnector http = new ServerConnector(server, acceptors, selectors, new HttpConnectionFactory(httpCfg));
        http.setName("http");
        http.setPort(cfg.getPort());
        server.addConnector(http);

        // servlets, filters, etc...
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");

        // custom 404s and other error handlers
        contextHandler.setErrorHandler(new CustomErrorHandler(requestErrorHandlers));

        // session timeout
        SessionHandler sessionHandler = contextHandler.getSessionHandler();
        sessionHandler.setMaxInactiveInterval((int) cfg.getSessionTimeout().getSeconds());

        // session cookies
        ServletContext context = contextHandler.getServletContext();
        SessionCookieConfig sessionCookieConfig = context.getSessionCookieConfig();
        sessionCookieConfig.setHttpOnly(true);
        sessionCookieConfig.setComment(cfg.getCookieComment());
        if (cfg.isSecureCookies()) {
            sessionCookieConfig.setSecure(true);
        }

        // init all @WebListeners
        for (ServletContextListener listener : contextListeners) {
            WebListener annotation = listener.getClass().getAnnotation(WebListener.class);
            if (annotation == null) {
                continue;
            }

            log.info("Event listener -> {}", listener.getClass());
            contextHandler.addEventListener(listener);
        }

        // init all @WebServlets
        for (HttpServlet servlet : servlets) {
            WebServlet annotation = servlet.getClass().getAnnotation(WebServlet.class);
            if (annotation == null) {
                continue;
            }

            ServletHolder holder = new ServletHolder(servlet);
            for (String pathSpec : annotation.value()) {
                log.info("Servlet -> {} @ {}", servlet.getClass(), pathSpec);
                contextHandler.addServlet(holder, pathSpec);
            }
        }

        for (ServletHolder holder : servletHolders) {
            WebServlet annotation = holder.getClass().getAnnotation(WebServlet.class);
            if (annotation == null) {
                continue;
            }

            for (String pathSpec : annotation.value()) {
                log.info("Servlet -> {} @ {}", holder.getClass(), pathSpec);
                contextHandler.addServlet(holder, pathSpec);
            }
        }

        // init all @WebFilters
        for (Filter filter : filters) {
            WebFilter annotation = filter.getClass().getAnnotation(WebFilter.class);
            if (annotation == null) {
                continue;
            }

            FilterHolder holder = new FilterHolder(filter);
            for (String pathSpec : annotation.value()) {
                log.info("Servlet -> {} @ {}", filter.getClass(), pathSpec);
                contextHandler.addFilter(holder, pathSpec, EnumSet.allOf(DispatcherType.class));
            }
        }

        for (FilterHolder holder : filterHolders) {
            WebFilter annotation = holder.getClass().getAnnotation(WebFilter.class);
            if (annotation == null) {
                continue;
            }

            for (String pathSpec : annotation.value()) {
                log.info("Filter -> {} @ {}", holder.getClass(), pathSpec);
                contextHandler.addFilter(holder, pathSpec, EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE, DispatcherType.ERROR));
            }
        }

        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
        contextHandlerCollection.addHandler(contextHandler);

        // additional handlers
        for (ContextHandlerConfigurator configurator : contextHandlerConfigurators) {
            log.info("Configuring additional context handlers {}...", configurator.getClass());
            configurator.configure(contextHandlerCollection);
        }

        StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.setHandler(contextHandlerCollection);
        server.setHandler(statisticsHandler);

        this.server = server;
    }

    public void start() throws Exception {
        this.server.start();
    }

    public void stop() throws Exception {
        this.server.stop();
    }

    private static RequestLog createRequestLog(ServerConfiguration cfg) {
        String path = cfg.getAccessLogPath();
        if (path == null) {
            log.warn("Access logs are not configured. Specify the ACCESS_LOG_PATH environment variable before starting the server.");
            return null;
        }

        log.info("Saving access logs into {}", path);

        RequestLogWriter writer = new AsyncRequestLogWriter(path);
        writer.setAppend(true);
        writer.setRetainDays(cfg.getAccessLogRetainDays());

        return new CustomRequestLog(writer, ServerConfiguration.ACCESS_LOG_FORMAT);
    }
}
