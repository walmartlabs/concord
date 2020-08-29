package com.walmartlabs.concord.client.v2;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.SecretsTaskCommon;
import com.walmartlabs.concord.client.SecretsTaskParams;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;

@Named("concordSecrets")
public class SecretsTaskV2 implements Task {

    private final SecretsTaskCommon delegate;

    @Inject
    public SecretsTaskV2(ApiClient apiClient, Context context) {
        this.delegate = new SecretsTaskCommon(apiClient, context.processConfiguration().projectInfo().orgName());
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        return delegate.execute(SecretsTaskParams.of(input));
    }
}
