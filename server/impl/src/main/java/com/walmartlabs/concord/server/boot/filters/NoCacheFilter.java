package com.walmartlabs.concord.server.boot.filters;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

@WebFilter({"/api/*", "/forms/*", "/cfg.js"})
public class NoCacheFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(NoCacheFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("NoCache filter enabled");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) response;
        httpResp.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        httpResp.setHeader("Pragma", "no-cache");
        httpResp.setHeader(HttpHeaders.EXPIRES, "0");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
