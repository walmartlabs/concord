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

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.web.env.EnvironmentLoader;
import org.apache.shiro.web.env.WebEnvironment;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.apache.shiro.web.filter.mgt.FilterChainResolver;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.Comparator;
import java.util.Set;

/**
 * Initializes Shiro's {@link org.apache.shiro.env.Environment}.
 * Responsible for applying all available {@link FilterChainConfigurator}.
 */
@WebListener
public class ShiroListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(ShiroListener.class);

    private final WebSecurityManager webSecurityManager;
    private final Set<FilterChainConfigurator> filterChainConfigurators;

    @Inject
    public ShiroListener(WebSecurityManager webSecurityManager,
                         Set<FilterChainConfigurator> filterChainConfigurators) {

        this.webSecurityManager = webSecurityManager;
        this.filterChainConfigurators = filterChainConfigurators;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        context.setAttribute(EnvironmentLoader.ENVIRONMENT_ATTRIBUTE_KEY, new WebEnvironment() {
            @Override
            public FilterChainResolver getFilterChainResolver() {
                PathMatchingFilterChainResolver resolver = new PathMatchingFilterChainResolver();
                FilterChainManager manager = resolver.getFilterChainManager();

                filterChainConfigurators.stream()
                        .sorted(Comparator.comparingInt(FilterChainConfigurator::priority))
                        .forEach(c -> {
                            log.info("Configuring chains {}...", c.getClass());
                            c.configure(manager);
                        });

                return resolver;
            }

            @Override
            public ServletContext getServletContext() {
                return context;
            }

            @Override
            public WebSecurityManager getWebSecurityManager() {
                return webSecurityManager;
            }

            @Override
            public SecurityManager getSecurityManager() {
                return webSecurityManager;
            }
        });
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        context.removeAttribute(EnvironmentLoader.ENVIRONMENT_ATTRIBUTE_KEY);
    }
}
