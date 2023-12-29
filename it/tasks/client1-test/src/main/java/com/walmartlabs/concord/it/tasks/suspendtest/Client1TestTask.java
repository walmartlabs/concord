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

import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named("client1Test")
public class Client1TestTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(Client1TestTask.class);

    private final ApiClientFactory factory;

    @Inject
    public Client1TestTask(ApiClientFactory factory) {
        this.factory = factory;
    }

    @Override
    public void execute(Context ctx) throws Exception {
        ProcessApi api = new ProcessApi(factory.create(ApiClientConfiguration.builder().context(ctx).build()));
        ProcessEntry p = api.get(ContextUtils.getTxId(ctx));
        log.info("process entry: {}", p.getStatus());
    }
}
