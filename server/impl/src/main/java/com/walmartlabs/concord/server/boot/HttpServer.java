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

import com.walmartlabs.concord.server.boot.resteasy.ApiDescriptor;
import com.walmartlabs.concord.server.cfg.ServerConfiguration;
import org.eclipse.jetty.ee8.nested.SessionHandler;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.ee8.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

public class HttpServer {

    private static final Logger log = LoggerFactory.getLogger(HttpServlet.class);

    private final Server server;

    @Inject
    public HttpServer(ServerConfiguration cfg,
                      Set<RequestErrorHandler> requestErrorHandlers,
                      Set<ServletContextListener> contextListeners,
                      Set<ApiDescriptor> apiDescriptors,
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
        // TODO remove once the '/' escaping is fixed in clients
        http.getConnectionFactory(HttpConnectionFactory.class)
                .getHttpConfiguration()
                .setUriCompliance(UriCompliance.LEGACY);
        server.addConnector(http);

        // servlets, filters, etc...
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");

        // custom 404s and other error handlers
        contextHandler.setErrorHandler(new CustomErrorHandler(requestErrorHandlers));

        // session timeout
        SessionHandler sessionHandler = contextHandler.getSessionHandler();
        log.info("Session timeout: {}", cfg.getSessionTimeout());
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
        contextListeners.stream().sorted(byPriority()).forEachOrdered(listener -> {
            WebListener annotation = listener.getClass().getAnnotation(WebListener.class);
            if (annotation == null) {
                return;
            }

            log.info("Event listener -> {}", listener.getClass());
            contextHandler.addEventListener(listener);
        });

        // init all Resteasy endpoints
        apiDescriptors.forEach(api -> {
            ServletHolder holder = new ServletHolder(HttpServletDispatcher.class);
            for (String pathSpec : api.paths()) {
                log.info("Serving API endpoints @ {}", pathSpec);
                contextHandler.addServlet(holder, pathSpec);
            }
        });

        // init all @WebServlets
        servlets.stream().sorted(byPriority()).forEachOrdered(servlet -> {
            WebServlet annotation = servlet.getClass().getAnnotation(WebServlet.class);
            if (annotation == null) {
                return;
            }

            ServletHolder holder = new ServletHolder(servlet);
            for (String pathSpec : annotation.value()) {
                log.info("Servlet -> {} @ {}", servlet.getClass(), pathSpec);
                contextHandler.addServlet(holder, pathSpec);
            }
        });

        servletHolders.stream().sorted(byPriority()).forEachOrdered(holder -> {
            WebServlet annotation = holder.getClass().getAnnotation(WebServlet.class);
            if (annotation == null) {
                return;
            }

            for (String pathSpec : annotation.value()) {
                log.info("Servlet -> {} @ {}", holder.getClass(), pathSpec);
                contextHandler.addServlet(holder, pathSpec);
            }
        });

        // init all @WebFilters
        filters.stream().sorted(byPriority()).forEachOrdered(filter -> {
            WebFilter annotation = filter.getClass().getAnnotation(WebFilter.class);
            if (annotation == null) {
                return;
            }

            FilterHolder holder = new FilterHolder(filter);
            for (String pathSpec : annotation.value()) {
                log.info("Filter -> {} @ {}", filter.getClass(), pathSpec);
                contextHandler.addFilter(holder, pathSpec, EnumSet.allOf(DispatcherType.class));
            }
        });

        filterHolders.stream().sorted(byPriority()).forEachOrdered(holder -> {
            WebFilter annotation = holder.getClass().getAnnotation(WebFilter.class);
            if (annotation == null) {
                return;
            }

            for (String pathSpec : annotation.value()) {
                log.info("Filter -> {} @ {}", holder.getClass(), pathSpec);
                contextHandler.addFilter(holder, pathSpec, EnumSet.allOf(DispatcherType.class));
            }
        });

        JettyWebSocketServletContainerInitializer.configure(contextHandler, null);

        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
        contextHandlerCollection.addHandler(contextHandler);

        // additional handlers
        contextHandlerConfigurators.stream().sorted(byPriority()).forEachOrdered(configurator -> {
            log.info("Configuring additional context handlers {}...", configurator.getClass());
            configurator.configure(contextHandlerCollection);
        });

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

    private static Comparator<Object> byPriority() {
        return Comparator.comparingInt(o -> {
            var a = o.getClass().getAnnotation(Priority.class);
            return a != null ? a.value() : 0;
        });
    }
}
