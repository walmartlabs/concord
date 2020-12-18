package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.sdk.ApiConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Named
@Singleton
public class ApiClientFactoryProvider implements Provider<ApiClientFactory> {

    private final ApiConfiguration cfg;
    private final WorkingDirectory workDir;

    @Inject
    public ApiClientFactoryProvider(ApiConfiguration cfg, WorkingDirectory workDir) {
        this.cfg = cfg;
        this.workDir = workDir;
    }

    @Override
    public ApiClientFactory get() {
        try {
            return new ApiClientFactoryImpl(cfg, workDir.getValue());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
