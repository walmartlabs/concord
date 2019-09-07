package com.walmartlabs.concord.plugins.kv;

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

import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessKvStoreApi;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named("kv")
public class KvTask implements Task {

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ApiClientFactory apiClientFactory;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @Inject
    public KvTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    public void remove(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClientFactory.create(context));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.removeKey(UUID.fromString(instanceId), key);
            return null;
        });
    }

    public void putString(@InjectVariable("txId") String instanceId, String key, String value) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClientFactory.create(context));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.putString(UUID.fromString(instanceId), key, value);
            return null;
        });
    }

    public String getString(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClientFactory.create(context));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.getString(UUID.fromString(instanceId), key));
    }

    public void putLong(@InjectVariable("txId") String instanceId, String key, Long value) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClientFactory.create(context));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.putLong(UUID.fromString(instanceId), key, value);
            return null;
        });
    }

    public long inc(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return incLong(instanceId, key);
    }

    public long incLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClientFactory.create(context));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.incLong(UUID.fromString(instanceId), key));
    }

    public Long getLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClientFactory.create(context));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.getLong(UUID.fromString(instanceId), key));
    }
}
