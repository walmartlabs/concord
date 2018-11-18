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

import com.squareup.okhttp.OkHttpClient;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.auth.ApiKeyAuth;

public class ConcordApiClient extends ApiClient {

    public ConcordApiClient(String baseUrl) {
        this(baseUrl, new OkHttpClient());
    }

    public ConcordApiClient(String baseUrl, OkHttpClient ok) {
        super(ok);
        setBasePath(baseUrl);
    }

    public ConcordApiClient setSessionToken(String token) {
        ApiKeyAuth apiKey = (ApiKeyAuth) getAuthentications().get("session_key");
        if (apiKey == null) {
            throw new RuntimeException("No session token authentication configured!");
        }
        apiKey.setApiKey(token);
        // TODO: remove me when swagger-maven-plugin 3.1.8 is released (in 3.1.7 the param's name always 'session_key')
        apiKey.setParamName("X-Concord-SessionToken");
        return this;
    }

    // TODO: remove me when swagger-maven-plugin 3.1.8 is released (in 3.1.7 the param's name always 'api_key')
    @Override
    public ApiClient setApiKey(String key) {
        ApiKeyAuth apiKey = (ApiKeyAuth) getAuthentications().get("api_key");
        if (apiKey == null) {
            throw new RuntimeException("No session token authentication configured!");
        }
        apiKey.setApiKey(key);
        apiKey.setParamName("Authorization");
        return this;
    }
}
