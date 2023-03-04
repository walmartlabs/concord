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

import org.eclipse.jetty.servlet.ServletHolder;
import org.sonatype.siesta.server.SiestaServlet;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;

/**
 * Binds {@link SiestaServlet} to Concord's API paths.
 */
@WebServlet({
        "/api/*",
        "/events/github/*"
})
public class SiestaServletHolder extends ServletHolder {

    @Inject
    public SiestaServletHolder(SiestaServlet siestaServlet) {
        super(siestaServlet);

        // necessary to support multiple API roots
        setInitParameter("resteasy.servlet.mapping.prefix", "/");
    }
}
