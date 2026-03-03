package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;

public class CliApiClientProvider implements Provider<ApiClient> {

    private final ApiClientFactory clientFactory;
    private final ApiKey apiKey;
    private final ProcessConfiguration processCfg;

    @Inject
    public CliApiClientProvider(ApiClientFactory clientFactory, ApiKey apiKey, ProcessConfiguration processCfg) {
        this.clientFactory = clientFactory;
        this.apiKey = apiKey;
        this.processCfg = processCfg;
    }

    @Override
    public ApiClient get() {
        return clientFactory.create(ApiClientConfiguration.builder()
                .apiKey(apiKey.value())
                .sessionToken(processCfg.processInfo().sessionToken())
                .build());
    }
}
