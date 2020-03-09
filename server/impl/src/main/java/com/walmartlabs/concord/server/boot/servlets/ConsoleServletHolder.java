package com.walmartlabs.concord.server.boot.servlets;

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
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.annotation.WebServlet;

@Named
@Singleton
@WebServlet("/*")
public class ConsoleServletHolder extends ServletHolder {

    private static final Logger log = LoggerFactory.getLogger(ConsoleServletHolder.class);

    @Inject
    public ConsoleServletHolder(ServerConfiguration cfg) {
        super(DefaultServlet.class);

        String path = cfg.getBaseResourcePath();
        if (path == null) {
            log.warn("BASE_RESOURCE_PATH environment variable must point to the Console files in order for the UI to work.");
            return;
        }

        log.info("Serving {} as /...", path);

        setInitParameter("dirAllowed", "false");
        setInitParameter("resourceBase", path);
        setInitParameter("pathInfoOnly", "true");
        setInitParameter("redirectWelcome", "false");
    }
}
