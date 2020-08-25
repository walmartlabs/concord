package com.walmartlabs.concord.runtime.v2.runner.sdk;

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

import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ApiConfigurationImpl implements ApiConfiguration {

    private final RunnerConfiguration runnerCfg;

    @Inject
    public ApiConfigurationImpl(RunnerConfiguration runnerCfg) {
        this.runnerCfg = runnerCfg;
    }

    @Override
    public String baseUrl() {
        return runnerCfg.api().baseUrl();
    }

    @Override
    public int connectTimeout() {
        return runnerCfg.api().connectTimeout();
    }

    @Override
    public int readTimeout() {
        return runnerCfg.api().readTimeout();
    }
}
