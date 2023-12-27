package com.walmartlabs.concord.client;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;

public abstract class AbstractConcordTask implements Task {

    protected static final String API_KEY = "apiKey";

    @Inject
    ApiConfiguration apiCfg;

    @Inject
    ApiClientFactory apiClientFactory;

    protected <T> T withClient(Context ctx, CheckedFunction<ApiClient, T> f) throws Exception {
        return withClient(ctx, true, f);
    }

    protected <T> T withClient(Context ctx, boolean withApiKey, CheckedFunction<ApiClient, T> f) throws Exception {
        ImmutableApiClientConfiguration.Builder builder = ApiClientConfiguration.builder()
                .baseUrl(getBaseUrl(ctx))
                .sessionToken(ContextUtils.getSessionToken(ctx));

        if (withApiKey) {
            builder.apiKey(getApiKey(ctx));
        }

        ImmutableApiClientConfiguration cfg = builder.build();

        return f.apply(apiClientFactory.create(cfg));
    }

    private String getApiKey(Context ctx) {
        return (String) ctx.getVariable(API_KEY);
    }

    // TODO move to ClientUtils?
    @SuppressWarnings("unchecked")
    private static String extractMessage(Object details) {
        if (details == null) {
            return null;
        }

        if (details instanceof List) {
            List<Object> l = (List<Object>) details;
            if (!l.isEmpty()) {
                Object o = l.get(0);
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    Object msg = m.get("message");
                    if (msg != null) {
                        return msg.toString();
                    }
                }
            }
        }

        return details.toString();
    }

    protected Map<String, Object> createCfg(Context ctx, String... keys) {
        Map<String, Object> m = new HashMap<>();

        String sessionToken = apiCfg.getSessionToken(ctx);
        if (sessionToken != null) {
            m.put(SESSION_TOKEN_KEY, sessionToken);
        }

        for (String k : keys) {
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }

        return m;
    }

    protected static String getBaseUrl(Context ctx) {
        Object v = ctx.getVariable(Keys.BASE_URL_KEY);
        if (v == null) {
            return null;
        }

        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Expected a string value '" + Keys.BASE_URL_KEY + "', got: " + v);
        }

        return (String) v;
    }

    @FunctionalInterface
    protected interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }
}