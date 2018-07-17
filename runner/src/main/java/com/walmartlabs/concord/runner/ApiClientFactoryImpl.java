package com.walmartlabs.concord.runner;

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
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ConcordApiClient;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class ApiClientFactoryImpl implements ApiClientFactory {

    private static final String CONNECT_TIMEOUT_KEY = "api.connect.timeout";
    private static final String READ_TIMEOUT_KEY = "api.read.timeout";

    private final ApiConfiguration cfg;
    private final Path tmpDir;
    private final OkHttpClient httpClient;

    @Inject
    public ApiClientFactoryImpl(ApiConfiguration cfg) throws IOException {
        this.cfg = cfg;
        this.tmpDir = IOUtils.createTempDir("task-client");

        this.httpClient = new OkHttpClient();

        int connectTimeout = Integer.parseInt(getEnv(CONNECT_TIMEOUT_KEY, "10000"));
        int readTimeout = Integer.parseInt(getEnv(READ_TIMEOUT_KEY, "60000"));

        this.httpClient.setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        this.httpClient.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
        this.httpClient.setWriteTimeout(30, TimeUnit.SECONDS);
    }

    @Override
    public ApiClient create(String sessionToken) {
        return new ConcordApiClient(cfg.getBaseUrl(), httpClient)
                .setSessionToken(sessionToken)
                .addDefaultHeader("Accept", "*/*")
                .setTempFolderPath(tmpDir.toString());
    }

    @Override
    public ApiClient create(Context ctx) {
        return create(cfg.getSessionToken(ctx));
    }

    private static String getEnv(String key, String def) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException(key + " must be specified");
    }
}
