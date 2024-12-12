package com.walmartlabs.concord.server.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import org.thymeleaf.web.servlet.JavaxServletWebApplication;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

@WebListener
public class ThymeleafApp implements ServletContextListener {

    public static final String APP_KEY = ThymeleafApp.class.getName();

    public static JavaxServletWebApplication getInstance(HttpServletRequest request) {
        var servletContext = request.getServletContext();
        var app = (JavaxServletWebApplication) servletContext.getAttribute(APP_KEY);
        if (app == null) {
            throw new IllegalStateException("ThymeleafApp is not available in the given ServletContext. This is a bug.");
        }
        return app;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        var servletContext = sce.getServletContext();
        var app = JavaxServletWebApplication.buildApplication(servletContext);
        servletContext.setAttribute(APP_KEY, app);
    }
}
