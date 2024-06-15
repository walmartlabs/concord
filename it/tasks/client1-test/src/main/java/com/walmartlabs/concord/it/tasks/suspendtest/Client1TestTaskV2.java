package com.walmartlabs.concord.it.tasks.suspendtest;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named("client1Test")
public class Client1TestTaskV2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(Client1TestTaskV2.class);

    private final Context ctx;
    private final ApiClient apiClient;
    private final ApiClientFactory factory;

    @Inject
    public Client1TestTaskV2(Context ctx, ApiClient apiClient, ApiClientFactory factory) {
        this.ctx = ctx;
        this.apiClient = apiClient;
        this.factory = factory;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        ProcessApi api = new ProcessApi(apiClient);

        ProcessEntry p = api.get(ctx.processInstanceId());
        log.info("process entry: {}", p.getStatus());

        return TaskResult.success();
    }
}
