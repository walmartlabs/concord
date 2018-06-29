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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Named;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class RequestInfoProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            req = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) req.get(InternalConstants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            req.put(InternalConstants.Request.ARGUMENTS_KEY, args);
        }

        if (args.containsKey(InternalConstants.Request.REQUEST_INFO_KEY)) {
            return chain.process(payload);
        }

        args.put(InternalConstants.Request.REQUEST_INFO_KEY, new HashMap<>());

        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, req);
        return chain.process(payload);
    }

    public static Map<String, Object> createRequestInfo(UriInfo i) {
        Map<String, Object> queryParams = new HashMap<>();
        for (Map.Entry<String, List<String>> e : i.getQueryParameters(true).entrySet()) {
            String k = e.getKey();
            List<String> v = e.getValue();

            if (v.size() == 1) {
                queryParams.put(k, v.get(0));
            } else {
                queryParams.put(k, v);
            }
        }

        Map<String, Object> m = new HashMap<>();
        m.put("uri", i.getRequestUri());
        m.put("query", queryParams);
        return m;
    }
}
