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

import com.walmartlabs.concord.server.RequestContext;
import com.walmartlabs.concord.server.RequestUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the initialization of {@link RequestContext} for each request.
 */
@WebFilter({"/api/*", "/logs/*", "/forms/*"})
public class RequestContextFilter implements Filter {

    private static final String REQUEST_ID_KEY = "_requestId";
    private static final String[] EXTRA_HEADER_KEYS = {RequestUtils.UI_REQUEST_HEADER};

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        UUID id = (UUID) request.getAttribute(REQUEST_ID_KEY);

        if (id == null) {
            id = UUID.randomUUID();
            request.setAttribute(REQUEST_ID_KEY, id);
        }

        Map<String, String> extraHeaders = new HashMap<>(EXTRA_HEADER_KEYS.length);
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            for (String k : EXTRA_HEADER_KEYS) {
                String s = httpReq.getHeader(k);
                if (s != null && !s.isEmpty()) {
                    extraHeaders.put(k, s);
                }
            }
        }

        try {
            RequestContext.set(id, request.getRemoteAddr(), extraHeaders);
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    @Override
    public void destroy() {
    }
}
