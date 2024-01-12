package com.walmartlabs.concord.runtime.v2.runner.remote;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

public class ApiClientProvider implements Provider<ApiClient> {

    private final ApiClientFactory clientFactory;
    private final ProcessConfiguration processCfg;

    @Inject
    public ApiClientProvider(ApiClientFactory clientFactory, ProcessConfiguration processCfg) {
        this.clientFactory = clientFactory;
        this.processCfg = processCfg;
    }

    @Override
    public ApiClient get() {
        String s = processCfg.processInfo().sessionToken();
        if (s == null) {
            throw new IllegalStateException("Can't initialize the API client: 'processInfo.sessionToken' is not defined.");
        }

        return clientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(s)
                .build());
    }
}
