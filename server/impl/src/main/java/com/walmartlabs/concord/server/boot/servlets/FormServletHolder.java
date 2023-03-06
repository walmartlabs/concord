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

import com.walmartlabs.concord.server.cfg.CustomFormConfiguration;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;

@WebServlet("/forms/*")
public class FormServletHolder extends ServletHolder {

    @Inject
    public FormServletHolder(CustomFormConfiguration cfg) {
        super(DefaultServlet.class);

        setInitParameter("acceptRanges", "true");
        setInitParameter("dirAllowed", "false");
        setInitParameter("resourceBase", cfg.getBaseDir().toAbsolutePath().toString());
        setInitParameter("pathInfoOnly", "true");
        setInitParameter("redirectWelcome", "false");
    }
}
