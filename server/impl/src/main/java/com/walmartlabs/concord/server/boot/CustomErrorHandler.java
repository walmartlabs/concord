package com.walmartlabs.concord.server.boot;

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

import org.eclipse.jetty.ee8.nested.ErrorHandler;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.http.HttpHeader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

public class CustomErrorHandler extends ErrorHandler {

    private final Set<RequestErrorHandler> handlers;

    public CustomErrorHandler(Set<RequestErrorHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        for (RequestErrorHandler h : handlers) {
            if (h.handle(request, response)) {
                // automatically set the correct Cache-Control headers
                String cacheControl = getCacheControl();
                if (cacheControl != null) {
                    response.setHeader(HttpHeader.CACHE_CONTROL.asString(), cacheControl);
                }
                return;
            }
        }

        super.handle(target, baseRequest, request, response);
    }
}
