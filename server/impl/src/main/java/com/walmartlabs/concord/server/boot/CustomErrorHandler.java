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

import org.eclipse.jetty.ee10.servlet.ErrorHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.Set;

public class CustomErrorHandler extends ErrorHandler {

    private final Set<RequestErrorHandler> handlers;

    public CustomErrorHandler(Set<RequestErrorHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        if (request instanceof ServletContextRequest servletRequest) {
            var httpRequest = servletRequest.getServletApiRequest();
            var httpResponse = servletRequest.getHttpServletResponse();
            for (RequestErrorHandler h : handlers) {
                if (h.handle(httpRequest, httpResponse)) {
                    // automatically set the correct Cache-Control headers
                    String cacheControl = getCacheControl();
                    if (cacheControl != null) {
                        httpResponse.setHeader(HttpHeader.CACHE_CONTROL.asString(), cacheControl);
                    }
                    callback.succeeded();
                    return true;
                }
            }
        }

        return super.handle(request, response, callback);
    }
}
