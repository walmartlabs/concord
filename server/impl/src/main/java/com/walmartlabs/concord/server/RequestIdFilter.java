package com.walmartlabs.concord.server;

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
import java.io.IOException;
import java.util.UUID;

public class RequestIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private static final String REQUEST_ID_KEY = "_requestId";

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("RequestIdFilter filter enabled");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        UUID id = (UUID) request.getAttribute(REQUEST_ID_KEY);

        if (id == null) {
            id = UUID.randomUUID();
            request.setAttribute(REQUEST_ID_KEY, id);
        }

        try {
            RequestId.set(id);
            chain.doFilter(request, response);
        } finally {
            RequestId.set(null);
        }
    }

    @Override
    public void destroy() {
    }
}
