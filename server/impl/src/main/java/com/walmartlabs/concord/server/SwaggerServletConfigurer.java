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

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.net.URL;

@Named
public class SwaggerServletConfigurer implements ServletConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SwaggerServletConfigurer.class);

    @Override
    public void configure(ServletContextHandler servletHandler) {
        URL url = ClassLoader.getSystemResource("com/walmartlabs/concord/server/api/swagger/");
        if (url == null) {
            log.warn("No Swagger definitions found.");
            return;
        }

        String baseDir = url.toExternalForm();

        ServletHolder h = new ServletHolder("swagger", DefaultServlet.class);
        h.setInitParameter("resourceBase", baseDir);
        h.setInitParameter("pathInfoOnly", "true");
        servletHandler.addServlet(h, "/swagger/*");

        log.info("Serving Swagger definitions from {}", baseDir);
    }
}
