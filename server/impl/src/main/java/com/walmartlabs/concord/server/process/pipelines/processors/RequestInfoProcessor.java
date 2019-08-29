package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.*;

@Named
public class RequestInfoProcessor implements PayloadProcessor {

    private static final Set<String> EXCLUDED_HEADERS = new HashSet<>(Arrays.asList(HttpHeaders.COOKIE, HttpHeaders.AUTHORIZATION));

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        if (args.containsKey(Constants.Request.REQUEST_INFO_KEY)) {
            return chain.process(payload);
        }

        args.put(Constants.Request.REQUEST_INFO_KEY, new HashMap<>());

        payload = payload.putHeader(Payload.CONFIGURATION, cfg);
        return chain.process(payload);
    }

    public static Map<String, Object> getRequestInfo(HttpServletRequest request) {
        String queryString = request.getQueryString() == null ? "" : "?" + request.getQueryString();

        Map<String, Object> m = new HashMap<>();
        m.put("remoteIp", request.getRemoteAddr());
        m.put("uri", request.getRequestURL() + queryString);
        m.put("headers", getHeaders(request));
        m.put("query", getQueryParams(request));
        return m;
    }

    private static Map<String, Object> getQueryParams(HttpServletRequest request) {
        Map<String, Object> queryParams = new HashMap<>();
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            String k = e.getKey();

            Object v = e.getValue();
            if (e.getValue().length == 1) {
                v = e.getValue()[0];
            }

            queryParams.put(k, v);
        }
        return queryParams;
    }

    private static Map<String, Object> getHeaders(HttpServletRequest request) {
        Map<String, Object> headers = new HashMap<>();
        List<String> headerNames = Collections.list(request.getHeaderNames());

        for (String h : headerNames) {
            if (EXCLUDED_HEADERS.contains(h)) {
                continue;
            }

            List<String> headerList = Collections.list(request.getHeaders(h));
            if (headerList.size() == 1) {
                headers.put(h, headerList.get(0));
            } else {
                headers.put(h, headerList);
            }
        }

        return headers;
    }
}
