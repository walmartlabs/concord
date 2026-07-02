package com.walmartlabs.concord.server.boot.filters;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Walmart Inc.
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

import javax.annotation.Priority;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/*")
@Priority(-1000)
public class TraceMethodFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceMethodFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("TRACE/TRACK method filter enabled");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpReq = (HttpServletRequest) request;
        var method = httpReq.getMethod();
        if ("TRACE".equalsIgnoreCase(method)) {
            var httpResp = (HttpServletResponse) response;
            httpResp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
