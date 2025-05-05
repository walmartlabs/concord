package com.walmartlabs.concord.plugins.ansible.v1;

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

import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.common.TimeProvider;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.DockerService;
import com.walmartlabs.concord.sdk.SecretService;

import javax.inject.Inject;
import javax.inject.Named;

@Named("ansible")
public class AnsibleTaskV1 extends RunPlaybookTask2 {

    @Inject
    public AnsibleTaskV1(ApiClientFactory apiClientFactory,
                         ApiConfiguration apiCfg,
                         SecretService secretService,
                         DockerService dockerService,
                         TimeProvider timeProvider) {

        super(apiClientFactory, apiCfg, secretService, dockerService, timeProvider);
    }
}
