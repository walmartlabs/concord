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

import com.walmartlabs.concord.server.cfg.ServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter({"/api/*", "/logs/*", "/forms/*"})
public class CORSFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CORSFilter.class);

    private final ServerConfiguration cfg;

    @Inject
    public CORSFilter(ServerConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("CORS filter enabled");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) response;
        httpResp.setHeader("Access-Control-Allow-Origin", cfg.getCORSConfiguration().getAllowOrigin());
        httpResp.setHeader("Access-Control-Allow-Methods", "*");
        httpResp.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Range, Cookie, Origin");
        httpResp.setHeader("Access-Control-Expose-Headers", "cache-control," +
                "content-language," +
                "expires," +
                "last-modified," +
                "content-range," +
                "content-length," +
                "accept-ranges");

        HttpServletRequest httpReq = (HttpServletRequest) request;
        if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            httpResp.setHeader("Allow", "OPTIONS, GET, POST, PUT, DELETE");
            httpResp.setStatus(204);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
