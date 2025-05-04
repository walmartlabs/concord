package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.*;

/**
 * Responsible for gathering process request parameters and converting
 * them into the process configuration.
 */
public class RequestParametersProcessor implements PayloadProcessor {

    private static final Set<String> EXCLUDED_HEADERS = new HashSet<>(Arrays.asList(HttpHeaders.COOKIE, HttpHeaders.AUTHORIZATION));

    @Override
    public Payload process(Chain chain, Payload payload) {
        HttpServletRequest request = payload.getHeader(Payload.SERVLET_REQUEST);

        // configuration values from the servlet request
        // remove the servlet request from the payload as it is not serializable
        Map<String, Object> requestCfg = parseRequest(request);

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        Map<String, Object> m = ConfigurationUtils.deepMerge(cfg, requestCfg);
        payload = payload.putHeader(Payload.CONFIGURATION, m);

        // one off operation, remove the request object when we done with it
        payload = payload.removeHeader(Payload.SERVLET_REQUEST);

        return chain.process(payload);
    }

    private static Map<String, Object> parseRequest(HttpServletRequest request) {
        Map<String, String[]> params = Collections.emptyMap();
        if (request != null) {
            params = request.getParameterMap();
        }

        Map<String, Object> args = new HashMap<>();
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("arguments.")) {
                continue;
            }
            k = k.substring("arguments.".length());

            Object v = e.getValue();
            if (e.getValue().length == 1) {
                v = e.getValue()[0];
            }

            Map<String, Object> m = ConfigurationUtils.toNested(k, v);
            args = ConfigurationUtils.deepMerge(args, m);
        }

        args.put(Constants.Request.REQUEST_INFO_KEY, getRequestInfo(request));

        return Collections.singletonMap(Constants.Request.ARGUMENTS_KEY, args);
    }

    private static Map<String, Object> getRequestInfo(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }

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
